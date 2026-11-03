window.PokemonDetails = (() => {
    "use strict";

    const cache = new Map();
    const panelRequests = new WeakMap();

    function pretty(value) {
        return String(value || "")
            .replace(/^kanto-/, "")
            .replace(/-area$/, "")
            .split("-")
            .map(word => /^[b]?\d+f$/i.test(word)
                ? word.toUpperCase()
                : word.charAt(0).toUpperCase() + word.slice(1))
            .join(" ");
    }

    function node(tag, text, className) {
        const result = document.createElement(tag);

        if (text !== undefined) {
            result.textContent = text;
        }

        if (className) {
            result.className = className;
        }

        return result;
    }

    function resourceId(resource) {
        const match = resource?.url?.match(/\/(\d+)\/?$/);
        return match ? Number(match[1]) : null;
    }

    function getDetails(id) {
        if (!cache.has(id)) {
            const request = fetch(`/api/pokemon/${id}/details`)
                .then(async response => {
                    if (!response.ok) {
                        throw new Error(
                            `Pokémon details returned HTTP ${response.status}`
                        );
                    }

                    return response.json();
                })
                .catch(error => {
                    cache.delete(id);
                    throw error;
                });

            cache.set(id, request);
        }

        return cache.get(id);
    }

    function levelRanges(levels) {
        const sorted = [...levels].sort((a, b) => a - b);
        const ranges = [];

        for (let i = 0; i < sorted.length; i++) {
            const start = sorted[i];
            let end = start;

            while (
                i + 1 < sorted.length &&
                sorted[i + 1] === end + 1
                ) {
                end = sorted[++i];
            }

            ranges.push(
                start === end ? String(start) : `${start}–${end}`
            );
        }

        return ranges.join(", ");
    }

    function methodName(method, area) {
        if (method === "walk") {
            const grass =
                area.includes("route-") ||
                area.includes("viridian-forest") ||
                area.includes("safari-zone");

            return grass ? "Tall grass" : "Walking";
        }

        const names = {
            surf: "Surf",
            "old-rod": "Old Rod",
            "good-rod": "Good Rod",
            "super-rod": "Super Rod",
            gift: "Gift",
            "gift-egg": "Gift Egg",
            "only-one": "Special encounter",
            "pokeflute": "Poké Flute"
        };

        return names[method] || pretty(method);
    }

    function encounterGroups(encounters, game) {
        const groups = new Map();

        for (const area of encounters || []) {
            for (const version of area.version_details || []) {
                if (version.version.name !== game) {
                    continue;
                }

                for (const detail of version.encounter_details || []) {
                    const conditions = (detail.condition_values || [])
                        .map(condition => condition.name)
                        .sort();

                    const key = JSON.stringify([
                        area.location_area.name,
                        detail.method.name,
                        conditions
                    ]);

                    if (!groups.has(key)) {
                        groups.set(key, {
                            area: area.location_area.name,
                            method: detail.method.name,
                            conditions,
                            levels: new Set()
                        });
                    }

                    const group = groups.get(key);

                    for (
                        let level = detail.min_level;
                        level <= detail.max_level;
                        level++
                    ) {
                        group.levels.add(level);
                    }
                }
            }
        }

        return [...groups.values()].sort((a, b) =>
            a.area.localeCompare(b.area, undefined, { numeric: true })
        );
    }

    function evolutionRules(details) {
        const results = [];

        for (const detail of details || []) {
            const trigger = detail.trigger?.name;

            // Later-game requirements do not apply to Red/Blue.
            const laterRequirements = [
                "gender",
                "held_item",
                "known_move",
                "known_move_type",
                "location",
                "min_happiness",
                "min_beauty",
                "min_affection",
                "relative_physical_stats",
                "party_species",
                "party_type",
                "trade_species"
            ];

            if (laterRequirements.some(field =>
                detail[field] !== null &&
                detail[field] !== undefined
            )) {
                continue;
            }

            if (
                detail.time_of_day ||
                detail.needs_overworld_rain ||
                detail.turn_upside_down
            ) {
                continue;
            }

            const introduced = detail.version_group?.name;

            if (introduced && introduced !== "red-blue") {
                continue;
            }

            if (trigger === "level-up" && detail.min_level) {
                results.push(`Level ${detail.min_level}`);
            }

            if (trigger === "trade") {
                results.push("Trade with another player");
            }

            if (trigger === "use-item") {
                const stone = detail.item?.name;

                if ([
                    "moon-stone",
                    "fire-stone",
                    "water-stone",
                    "thunder-stone",
                    "leaf-stone"
                ].includes(stone)) {
                    results.push(`Use ${pretty(stone)}`);
                }
            }
        }

        return [...new Set(results)];
    }

    function evolutionEdges(chain) {
        const edges = [];

        function visit(parent) {
            if (!parent?.species) {
                return;
            }

            for (const child of parent.evolves_to || []) {
                const fromId = resourceId(parent.species);
                const toId = resourceId(child.species);

                if (
                    fromId >= 1 && fromId <= 151 &&
                    toId >= 1 && toId <= 151
                ) {
                    edges.push({
                        from: pretty(parent.species.name),
                        to: pretty(child.species.name),
                        rules: evolutionRules(child.evolution_details)
                    });
                }

                visit(child);
            }
        }

        visit(chain);
        return edges;
    }

    function addFact(panel, label, value) {
        const row = node("p");
        row.append(
            node("strong", label + ": "),
            document.createTextNode(String(value))
        );
        panel.appendChild(row);
    }

    function spriteFor(pokemon, instance) {
        if (instance?.shiny) {
            return instance.gender === "FEMALE"
                ? pokemon.shinyFemaleSprite ??
                pokemon.shinySprite ??
                pokemon.sprite
                : pokemon.shinySprite ?? pokemon.sprite;
        }

        return instance?.gender === "FEMALE"
            ? pokemon.femaleSprite ?? pokemon.sprite
            : pokemon.sprite;
    }

    async function show(id, panel, options = {}) {
        const requestId = {};
        panelRequests.set(panel, requestId);

        panel.replaceChildren(node("p", "Loading Pokémon details..."));

        try {
            const data = await getDetails(id);

            // Do not overwrite a more recently selected Pokémon.
            if (panelRequests.get(panel) !== requestId) {
                return;
            }

            const pokemon = data.pokemon;
            const instance = options.instance;

            const requestedGame =
                String(options.game || "").toLowerCase();

            const fixedGame = ["red", "blue"].includes(requestedGame)
                ? requestedGame
                : null;

            const nodes = [];
            const sprite = spriteFor(pokemon, instance);

            if (sprite) {
                const image = node("img");
                image.src = sprite;
                image.alt = pretty(pokemon.name);
                image.width = 180;
                image.height = 180;

                image.addEventListener("error", () => {
                    image.hidden = true;
                }, { once: true });

                nodes.push(image);
            }

            nodes.push(node(
                "h3",
                instance?.nickname || pretty(pokemon.name)
            ));

            panel.replaceChildren(...nodes);

            addFact(
                panel,
                "National Pokédex",
                "#" + String(pokemon.id).padStart(3, "0")
            );

            addFact(panel, "Species", pretty(pokemon.name));
            addFact(panel, "Type", pokemon.types.map(pretty).join(" / "));

            if (instance) {
                addFact(panel, "Nickname", instance.nickname || "None");
                addFact(panel, "Gender", instance.gender || "Unknown");
                addFact(panel, "Shiny", instance.shiny ? "Yes" : "No");

                if (instance.level != null) {
                    addFact(panel, "Level", instance.level);
                }

                addFact(
                    panel,
                    "Currently owned",
                    instance.currentlyOwned ? "Yes" : "No"
                );

                if (instance.obtainedAt) {
                    addFact(
                        panel,
                        "Recorded",
                        new Date(instance.obtainedAt).toLocaleDateString()
                    );
                }
            }

            const gameLabel = node("label", "Game ");
            const select = node("select");

            for (const game of ["red", "blue"]) {
                const option = node("option", "Pokémon " + pretty(game));
                option.value = game;
                select.appendChild(option);
            }

            select.value = fixedGame || "red";
            select.disabled = Boolean(fixedGame);

            gameLabel.appendChild(select);
            panel.appendChild(gameLabel);

            panel.appendChild(node("h3", "Evolution in Red / Blue"));

            const edges = evolutionEdges(data.evolutionChain);

            if (edges.length === 0) {
                panel.appendChild(node(
                    "p",
                    "No evolution in Red or Blue."
                ));
            } else {
                for (const edge of edges) {
                    panel.appendChild(node(
                        "p",
                        `${edge.from} → ${edge.to}: ` +
                        (edge.rules.length > 0
                            ? edge.rules.join(" OR ")
                            : "Evolution condition needs verification.")
                    ));
                }
            }

            panel.appendChild(node("h3", "Game availability"));

            const availability = node("div");

            for (const game of ["red", "blue"]) {

                const groups = encounterGroups(data.encounters, game);

                const text = groups.length > 0 ? "Locations or acquisition methods listed below."
                    : "No direct acquisition listed. May require evolution, trade, a gift or an event.";

                availability.appendChild(node("p", `${pretty(game)}: ${text}`));
            }

            panel.appendChild(availability);
            panel.appendChild(node("h3", "Where and how to obtain"));

            const locations = node("div", undefined, "pokemon-capture-list");
            panel.appendChild(locations);

            function renderLocations() {

                const groups = encounterGroups(data.encounters, select.value);

                locations.replaceChildren();

                if (groups.length === 0) {
                    locations.appendChild(node("p", "PokéAPI lists no direct encounters for this version. " +
                        "This does not necessarily mean the Pokémon is unavailable."));
                    return;
                }

                const list = node("ul");

                for (const group of groups) {

                    const conditions = group.conditions.length > 0 ? " · " + group.conditions.map(pretty).join(", ") : "";

                    list.appendChild(node("li", `${pretty(group.area)} · ` + `${methodName(group.method, group.area)} · ` +
                        `Lv. ${levelRanges(group.levels)}` + conditions));
                }

                locations.appendChild(list);
            }

            select.addEventListener("change", renderLocations);
            renderLocations();

        } catch (error) {
            if (panelRequests.get(panel) !== requestId) {
                return;
            }

            const retry = node("button", "Retry");
            retry.type = "button";

            retry.addEventListener("click", () => {

                show(id, panel, options);
            });

            panel.replaceChildren(node("p", "Could not load Pokémon details."), retry);

            console.error("Pokémon details failed:", error);
        }
    }

    return { show };
})();