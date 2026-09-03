import { join, resolve } from "node:path";
import {
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle,
    ContainerBuilder,
    MessageFlags,
    SectionBuilder,
    TextDisplayBuilder,
    ThumbnailBuilder,
    WebhookClient,
    type WebhookMessageCreateOptions,
} from "discord.js";

const PROJECT_ROOT = resolve(import.meta.dir, "../..");
const CHANGELOG_FILE = join(PROJECT_ROOT, "CHANGELOG_LATEST.md");
const PUBLISH_RESULT_GLOB = "versions/*/build/publishMods/publishModrinth.json";
const ICON_URL =
    "https://raw.githubusercontent.com/LutzLuca/BtrBz/master/src/main/resources/assets/btrbz/icon.png";

const MAX_BUTTONS_PER_ROW = 5;
const MAX_CHANGELOG_LENGTH = 3_900;
const BTRBZ_COLOR = 0xff0a8a;
const MODRINTH_COLOR = 0x1bd96a;

const CHANGELOG_SECTIONS: Record<string, { emoji: string; title: string }> = {
    breaking: {
        emoji: "<:icons_warning:1544806124807655454>",
        title: "Breaking changes",
    },
    added: {
        emoji: "<:icons_plus:1544802643548053605>",
        title: "Added",
    },
    changed: {
        emoji: "<:icons_up:1544803454478717070>",
        title: "Improved",
    },
    fixed: {
        emoji: "<:icon_fixed:1544802965963935765>",
        title: "Fixed",
    },
    removed: {
        emoji: "<:icon_minus:1544802852294103091>",
        title: "Removed",
    },
    notes: {
        emoji: "<:icons_info:1544829413273763901>",
        title: "Notes",
    },
    internal: {
        emoji: "<:icons_code:1544831268943036550>",
        title: "Internal",
    },
};

/**
 * Mod Publish Plugin writes one result to build/publishMods/<task>.json.
 * A Modrinth result looks like:
 * { "type": "modrinth", "id": "...", "projectId": "...", "title": "..." }
 *
 * https://github.com/modmuss50/mod-publish-plugin/blob/main/src/main/kotlin/me/modmuss50/mpp/PublishModTask.kt
 * https://github.com/modmuss50/mod-publish-plugin/blob/main/src/main/kotlin/me/modmuss50/mpp/platforms/modrinth/Modrinth.kt
 */
interface ModrinthPublishResult {
    type: "modrinth";
    id: string;
    projectId: string;
    title: string;
}

interface Download {
    label: string;
    url: string;
}

function createChangelog(version: string, rawChangelog: string): string {
    const content: string[] = [];
    let skippedReleaseHeading = false;

    for (const line of rawChangelog.split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!skippedReleaseHeading && trimmed.startsWith("## [")) {
            skippedReleaseHeading = true;
            continue;
        }

        if (trimmed.startsWith("### ")) {
            const section = trimmed.slice(4).trim();
            const style = CHANGELOG_SECTIONS[section.toLowerCase()];
            content.push(
                style ? `## ${style.emoji} ${style.title}` : `## ${section}`,
            );
            continue;
        }

        content.push(line);
    }

    const body = content
        .join("\n")
        .trim()
        .replace(/\n{3,}/g, "\n\n");
    const description =
        `# BtrBz v${version} is out!` + (body ? `\n\n${body}` : "");
    if (description.length <= MAX_CHANGELOG_LENGTH) return description;

    const suffix =
        "\n\n*The full changelog is available from the download links below.*";
    return (
        description.slice(0, MAX_CHANGELOG_LENGTH - suffix.length).trimEnd() +
        suffix
    );
}

function createButtonRows(
    downloads: Download[],
): ActionRowBuilder<ButtonBuilder>[] {
    const rows: ActionRowBuilder<ButtonBuilder>[] = [];

    for (
        let index = 0;
        index < downloads.length;
        index += MAX_BUTTONS_PER_ROW
    ) {
        rows.push(
            new ActionRowBuilder<ButtonBuilder>().addComponents(
                downloads
                    .slice(index, index + MAX_BUTTONS_PER_ROW)
                    .map((download) =>
                        new ButtonBuilder()
                            .setStyle(ButtonStyle.Link)
                            .setLabel(download.label)
                            .setURL(download.url),
                    ),
            ),
        );
    }

    return rows;
}

function createMessage(
    version: string,
    changelog: string,
    downloads: Download[],
    roleId?: string,
): WebhookMessageCreateOptions {
    const changelogContainer = new ContainerBuilder()
        .setAccentColor(BTRBZ_COLOR)
        .addSectionComponents(
            new SectionBuilder()
                .addTextDisplayComponents(
                    new TextDisplayBuilder().setContent(
                        createChangelog(version, changelog),
                    ),
                )
                .setThumbnailAccessory(
                    new ThumbnailBuilder()
                        .setURL(ICON_URL)
                        .setDescription("BtrBz logo"),
                ),
        );

    const downloadContainer = new ContainerBuilder()
        .setAccentColor(MODRINTH_COLOR)
        .addTextDisplayComponents(
            new TextDisplayBuilder().setContent(
                `**[v${version} - BtrBz](${downloads[0]!.url})**\nDownload BtrBz v${version} on Modrinth.`,
            ),
        )
        .addActionRowComponents(createButtonRows(downloads));

    return {
        username: "BtrBz Releases",
        avatarURL: ICON_URL,
        allowedMentions: {
            parse: [],
            roles: roleId ? [roleId] : [],
        },
        components: [
            ...(roleId
                ? [new TextDisplayBuilder().setContent(`<@&${roleId}>`)]
                : []),
            changelogContainer,
            downloadContainer,
        ],
        flags: MessageFlags.IsComponentsV2,
        withComponents: true,
    };
}

async function readDownloads(): Promise<Download[]> {
    const downloads: Download[] = [];
    const resultFiles = new Bun.Glob(PUBLISH_RESULT_GLOB);

    for await (const resultFile of resultFiles.scan({ cwd: PROJECT_ROOT })) {
        const result = (await Bun.file(
            join(PROJECT_ROOT, resultFile),
        ).json()) as ModrinthPublishResult;
        downloads.push({
            label: result.title,
            url: `https://modrinth.com/mod/${result.projectId}/version/${result.id}`,
        });
    }

    return downloads.sort((left, right) =>
        left.label.localeCompare(right.label),
    );
}

async function main(): Promise<void> {
    const changelog = await Bun.file(CHANGELOG_FILE).text();
    const version = changelog.match(/^## \[([^\]]+)]/m)?.[1];
    if (!version) throw new Error("CHANGELOG_LATEST.md has no release heading");

    const downloads = await readDownloads();
    if (downloads.length === 0) throw new Error("No Modrinth releases found");

    const webhookUrl = process.env.DISCORD_WEBHOOK;
    if (!webhookUrl) throw new Error("Missing DISCORD_WEBHOOK");

    const webhook = new WebhookClient({ url: webhookUrl });
    try {
        await webhook.send(
            createMessage(
                version,
                changelog,
                downloads,
                process.env.DISCORD_RELEASE_ROLE_ID?.trim(),
            ),
        );
    } finally {
        webhook.destroy();
    }

    console.log("Discord release announcement posted");
}

await main();
