import { describe, expect, test } from "bun:test";
import { MessageFlags } from "discord.js";
import { createChangelog, createMessage } from "./discord-release";

describe("Discord announcement", () => {
    const downloads = [
        { label: "26.1.x", url: "https://modrinth.com/mod/test/version/first" },
        { label: "26.2", url: "https://modrinth.com/mod/test/version/second" },
    ];
    test("preserves the Components V2 layout and explicit role mention", () => {
        const message = createMessage(
            "1.2.3-alpha",
            "## [1.2.3-alpha] - 2026-09-08\n\n### Changed\n\n- Faster builds",
            downloads,
            "123",
        );
        expect(message.flags).toBe(MessageFlags.IsComponentsV2);
        expect(message.allowedMentions).toEqual({ parse: [], roles: ["123"] });
        const content = JSON.stringify(message.components);
        expect(content).toContain("Improved");
        expect(content).toContain("<@&123>");
        for (const download of downloads)
            expect(content).toContain(download.url);
        expect(message.components).toHaveLength(3);
    });
    test("omits mention without a role and requires downloads", () => {
        const message = createMessage("1.2.3-alpha", "", downloads);
        expect(message.allowedMentions).toEqual({ parse: [], roles: [] });
        expect(message.components).toHaveLength(2);
        expect(() => createMessage("1.2.3-alpha", "", [])).toThrow(
            "No Modrinth",
        );
    });
    test("limits lengthy changelogs with a full-changelog link hint", () => {
        const text = createChangelog("1.2.3-alpha", "x".repeat(5_000));
        expect(text.length).toBeLessThanOrEqual(3_900);
        expect(text).toContain("full changelog");
    });
});
