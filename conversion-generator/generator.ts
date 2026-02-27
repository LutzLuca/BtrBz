import { type, Type } from "arktype";
import * as fs from "fs";

const partialBazaarProduct = type({
    product_id: "string",
});

const partialBazaarResponse = type({
    success: "boolean",
    lastUpdated: "number",
    products: { "[string]": partialBazaarProduct },
});

const partialItem = type({
    id: "string",
    name: "string",
});

const partialItemsResponse = type({
    success: "boolean",
    lastUpdated: "number",
    items: partialItem.array(),
});

// According to https://minecraft.wiki/w/Formatting_codes this regex should match all valid
// Minecraft formatting codes, that are used in names like '§3Blue Goblin Egg'
const FORMATTING_CODES = /§[0-9a-v]/g;

// Some names from the Items API have placeholders like %%red%% or %%italic%%
// see '%%red%%Volcanic Rock'
const PLACEHOLDER = /%%\w+%%/g;

const ENDS_WITH_INTEGER = /\d+$/;

const ROMAN_NUMERALS: Record<number, string> = {
    1: "I",
    2: "II",
    3: "III",
    4: "IV",
    5: "V",
    6: "VI",
    7: "VII",
    8: "VIII",
    9: "IX",
    10: "X",
    20: "XX",
};

const NAME_OVERRIDES: Record<string, string> = {
    ENCHANTMENT_ULTIMATE_WISE_1: "Ultimate Wise I",
    ENCHANTMENT_ULTIMATE_WISE_2: "Ultimate Wise II",
    ENCHANTMENT_ULTIMATE_WISE_3: "Ultimate Wise III",
    ENCHANTMENT_ULTIMATE_WISE_4: "Ultimate Wise IV",
    ENCHANTMENT_ULTIMATE_WISE_5: "Ultimate Wise V",

    ENCHANTMENT_ULTIMATE_JERRY_1: "Ultimate Jerry I",
    ENCHANTMENT_ULTIMATE_JERRY_2: "Ultimate Jerry II",
    ENCHANTMENT_ULTIMATE_JERRY_3: "Ultimate Jerry III",
    ENCHANTMENT_ULTIMATE_JERRY_4: "Ultimate Jerry IV",
    ENCHANTMENT_ULTIMATE_JERRY_5: "Ultimate Jerry V",

    ENCHANTMENT_ULTIMATE_REITERATE_1: "Duplex I",
    ENCHANTMENT_ULTIMATE_REITERATE_2: "Duplex II",
    ENCHANTMENT_ULTIMATE_REITERATE_3: "Duplex III",
    ENCHANTMENT_ULTIMATE_REITERATE_4: "Duplex IV",
    ENCHANTMENT_ULTIMATE_REITERATE_5: "Duplex V",

    ENCHANTMENT_ULTIMATE_BOBBIN_TIME_3: "Bobbin' Time III",
    ENCHANTMENT_ULTIMATE_BOBBIN_TIME_4: "Bobbin' Time IV",
    ENCHANTMENT_ULTIMATE_BOBBIN_TIME_5: "Bobbin' Time V",

    ENCHANTMENT_PRISTINE_1: "Prismatic I",
    ENCHANTMENT_PRISTINE_2: "Prismatic II",
    ENCHANTMENT_PRISTINE_3: "Prismatic III",
    ENCHANTMENT_PRISTINE_4: "Prismatic IV",
    ENCHANTMENT_PRISTINE_5: "Prismatic V",

    ENCHANTMENT_TURBO_CACTUS_1: "Turbo-Cacti I",
    ENCHANTMENT_TURBO_CACTUS_2: "Turbo-Cacti II",
    ENCHANTMENT_TURBO_CACTUS_3: "Turbo-Cacti III",
    ENCHANTMENT_TURBO_CACTUS_4: "Turbo-Cacti IV",
    ENCHANTMENT_TURBO_CACTUS_5: "Turbo-Cacti V",

    ENCHANTMENT_COUNTER_STRIKE_3: "Counter-Strike III",
    ENCHANTMENT_COUNTER_STRIKE_4: "Counter-Strike IV",
    ENCHANTMENT_COUNTER_STRIKE_5: "Counter-Strike V",

    ENCHANTMENT_SYPHON_4: "Drain IV",
    ENCHANTMENT_SYPHON_5: "Drain V",

    ENCHANTMENT_TRIPLE_STRIKE_5: "Triple-Strike V",
    ENCHANTMENT_DRAGON_HUNTER_6: "Gravity VI",
    ENCHANTMENT_ARCANE_6: "Woodsplitter VI",

    SHARD_CINDER_BAT: "Cinderbat Shard",
    SHARD_ENDSTONE_PROTECTOR: "End Stone Protector Shard",
    SHARD_SEA_ARCHER: "Bogged Shard",
    SHARD_STRIDER_SURFER: "Stridersurfer Shard",
};

const GEM_SYMBOLS: Record<string, string> = {
    AMBER: "⸕",
    AMETHYST: "❈",
    AQUAMARINE: "☂",
    CITRINE: "☘",
    JADE: "☘",
    JASPER: "❁",
    ONYX: "☠",
    OPAL: "❂",
    PERIDOT: "☘",
    RUBY: "❤",
    SAPPHIRE: "✎",
    TOPAZ: "✧",
};

const GEM_RARITY: Record<string, string> = {
    ROUGH: "Rough",
    FLAWED: "Flawed",
    FINE: "Fine",
    FLAWLESS: "Flawless",
    PERFECT: "Perfect",
};

function tryParseGemstoneId(id: string): string | undefined {
    // Expect patterns like <RARITY>_<GEM>_GEM
    const parts = id.split("_");
    if (parts.length != 3) {
        return undefined;
    }

    let [rarityPart, type, gem] = parts;
    if (gem !== "GEM") {
        return undefined;
    }

    const rarity = GEM_RARITY[rarityPart!];
    if (!rarity) {
        console.error(`Unknown gem rarity "${rarityPart}" in id "${id}"`);
        return undefined;
    }

    const symbol = GEM_SYMBOLS[type!];
    if (!symbol) {
        console.error(`Unknown gem type "${type}" in id "${id}"`);
        return undefined;
    }
    return `${symbol} ${rarity} ${screamingSnakeCaseToTitleCase(
        type!,
    )} Gemstone`;
}

function screamingSnakeCaseToTitleCase(input: string): string {
    return input
        .toLowerCase()
        .split("_")
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
}

function cleanName(name: string): string {
    return name.replace(FORMATTING_CODES, "").replace(PLACEHOLDER, "").trim();
}

function idToName(id: string): string {
    if (NAME_OVERRIDES[id]) {
        return NAME_OVERRIDES[id];
    }
    const gemName = tryParseGemstoneId(id);
    if (gemName) {
        return gemName;
    }

    // while currently no bz product id contains any formatting codes / placehorders might as well strip them here
    const cleanedId = cleanName(id)
        .replace(/^ENCHANTMENT_/, "")
        .replace(/^ULTIMATE_/, "")
        .trim();

    let name = screamingSnakeCaseToTitleCase(cleanedId);
    name = romanizeTrailingInteger(name);

    if (name.startsWith("Shard ")) {
        name = `${name.slice("Shard ".length)} Shard`;
    } else if (cleanedId.startsWith("ESSENCE_") && name.startsWith("Essence")) {
        name = `${name.slice("Essence ".length)} Essence`;
    } else if (cleanedId.startsWith("TURBO_") && name.startsWith("Turbo ")) {
        name = `Turbo-${name.slice("Turbo ".length)}`;
    }

    return name.trim();
}

function romanizeTrailingInteger(name: string): string {
    const match = ENDS_WITH_INTEGER.exec(name);
    if (!match) {
        return name;
    }

    const num = Number(match[0]);
    const numeral = ROMAN_NUMERALS[num];

    if (!numeral) {
        console.warn(`No roman numeral mapping for ${num} in "${name}"`);
        return name;
    }
    return name.replace(ENDS_WITH_INTEGER, numeral);
}

async function validatedFetch<T extends Type>(
    url: string,
    schema: T,
    label: string,
): Promise<T["infer"]> {
    console.log(`Fetching ${label} from ${url}`);
    const resp = await fetch(url);
    const json = await resp.json();

    const parsed = schema(json);
    if (parsed instanceof type.errors) {
        throw new Error(`Failed to deserialize ${label}: ${parsed.summary}`);
    }
    return parsed;
}

const fetchBazaarData = () =>
    validatedFetch(
        "https://api.hypixel.net/v2/skyblock/bazaar",
        partialBazaarResponse,
        "Bazaar Data",
    );

const fetchItemData = () =>
    validatedFetch(
        "https://api.hypixel.net/v2/resources/skyblock/items",
        partialItemsResponse,
        "Item Data",
    );

async function createMapping(outputFile = "conversions.json") {
    console.log("Starting mapping creation...");

    const [bazaarData, itemData] = await Promise.all([
        fetchBazaarData(),
        fetchItemData(),
    ]);

    console.log(
        `Fetched ${Object.keys(bazaarData.products).length} bazaar products`,
        `Fetched ${itemData.items.length} items from items API`,
    );

    const itemIdToName = Object.fromEntries(
        itemData.items.map(({ id, name }) => [id, name]),
    );

    let usedFromItems = 0;
    let constructedManually = 0;

    const conversions = Object.fromEntries(
        Object.keys(bazaarData.products)
            .map((id) => {
                const gemName = tryParseGemstoneId(id);
                if (gemName) {
                    constructedManually++;
                    return [id, gemName];
                }

                const fromItems = id in itemIdToName;
                fromItems ? usedFromItems++ : constructedManually++;

                const name =
                    fromItems ? cleanName(itemIdToName[id]!) : idToName(id);
                return [id, name];
            })
            .sort(([idA], [idB]) => idA!.localeCompare(idB!)),
    );

    fs.writeFileSync(outputFile, JSON.stringify(conversions, null, 4));

    console.log(
        `Mapping written to ${outputFile} (items API: ${usedFromItems}, manually constructed: ${constructedManually})`,
    );
}

const outputFile = process.argv[2] || "conversions.json";

createMapping(outputFile).catch((err) => {
    console.error("Error during mapping creation:", err);
    process.exit(1);
});
