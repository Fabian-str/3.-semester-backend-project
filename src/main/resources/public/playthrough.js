(() => {
    "use strict";

    const token = localStorage.getItem("token");

    const playthroughId =
        new URLSearchParams(window.location.search).get("id");

    if (!token) {
        window.location.replace("/");
        return;
    }

    if (!playthroughId || !/^\d+$/.test(playthroughId)) {
        window.location.replace("/dashboard.html");
        return;
    }

    const apiPath = `/api/playthroughs/${playthroughId}`;

    const pokemonCache = new Map();

    let lastPartyKey = null;
    let pollingStopped = false;
    let pollTimer;

    function emptyPartySlot(slot) {
        const card = document.createElement("article");
        card.className = "pt-pokemon-card pt-empty-slot";
        const number = document.createElement("span");
        number.className = "pt-slot-number";
        number.textContent = String(slot);
        const label = document.createElement("strong");
        label.textContent = "Empty slot";
        card.append(number, label);
        return card;
    }

    function renderProgress(state) {
        for (const [field, id] of [
            ["seenPokemonIds", "seen-count"],
            ["caughtPokemonIds", "caught-count"]
        ]) {
            const ids = state?.[field];

            const count = Array.isArray(ids) ? new Set(ids.filter(id =>
                    Number.isInteger(id) && id >= 1 && id <= 151)).size : null;

            element(id).textContent = count === null ? "—" : String(count);
        }

        const hasPokedex = Array.isArray(state?.seenPokemonIds) && Array.isArray(state?.caughtPokemonIds);

        element("pokedex-notice").textContent = hasPokedex
            ? "Pokédex from your latest synced save." : "Waiting for Pokédex data.";

        const badgeMask = state?.badgeMask;

        const hasBadgeData = Number.isInteger(badgeMask) && badgeMask >= 0 && badgeMask <= 255;

        const badges = document.querySelectorAll(".pt-badge");

        let badgeCount = 0;

        badges.forEach((badge, index) => {
            const earned = hasBadgeData ? (badgeMask & (1 << index)) !== 0 : null;

            if (earned) {
                badgeCount++;
            }

            badge.dataset.earned = earned === null ? "unknown" : String(earned);

            const name = badge.querySelector("strong").textContent;

            const description = earned === null
                ? "Waiting for badge data" : earned ? "Earned" : "Not earned";

            badge.title = `${name}: ${description}`;
            badge.setAttribute("aria-label", `${name} Badge: ${description}`);
        });

        element("badge-count").textContent = hasBadgeData ? String(badgeCount) : "—";

        const badgeGrid = document.querySelector(".pt-badge-grid");

        badgeGrid.setAttribute("aria-label", hasBadgeData
                ? `${badgeCount} of 8 badges earned` : "Waiting for badge data");

        element("badge-notice").textContent = hasBadgeData
            ? "Badges from your save." : "Waiting for badge data.";
    }

    function element(id) {
        return document.getElementById(id);
    }

    function setStatus(text, status) {
        element("emulator-status").textContent = text;
        element("emulator-status").dataset.status = status;
    }

    async function authorizedFetch(url, options = {}) {

        const response = await fetch(url, {
            ...options, headers: {...options.headers, "Authorization": "Bearer " + token}
        });

        if (response.status === 401) {
            pollingStopped = true;
            localStorage.removeItem("token");
            window.location.replace("/");

            throw new Error("Session expired");
        }

        return response;
    }

    function displayName(value) {

        return (value || "Unknown Pokémon").split("-").map(word =>
            word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
    }

    // Duplicate species share the same pending or completed request.
    function getPokemon(pokemonId) {

        const cached = pokemonCache.get(pokemonId);

        if (cached && Date.now() < cached.expiresAt) {
            return cached.promise;
        }

        const entry = {
            expiresAt: Infinity
        };

        entry.promise = fetch(`/api/pokemon/${pokemonId}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error("Pokémon metadata unavailable");
                }

                return response.json();
            })
            .catch(error => {
                entry.expiresAt = Date.now() + 30000;

                console.error(`Could not load Pokémon #${pokemonId}:`, error);

                return null;
            });

        pokemonCache.set(pokemonId, entry);

        return entry.promise;
    }

    function getPokemonSprite(pokemon, instance) {

        if (!pokemon) {
            return null;
        }

        if (instance?.shiny) {
            return instance.gender === "FEMALE" ? pokemon.shinyFemaleSprite
                ?? pokemon.shinySprite ?? pokemon.sprite : pokemon.shinySprite ?? pokemon.sprite;
        }

        return instance?.gender === "FEMALE" ? pokemon.femaleSprite ?? pokemon.sprite : pokemon.sprite;
    }

    function createPokemonCard(pokemonId, pokemon, instance = null, slot = null) {
        const card = document.createElement("article");
        card.className = "pt-pokemon-card";
        card.dataset.shiny = String(Boolean(instance?.shiny));

        const speciesName = pokemon ? displayName(pokemon.name) : `Pokémon #${pokemonId}`;

        const sprite = getPokemonSprite(pokemon, instance);

        if (sprite) {
            const image = document.createElement("img");

            image.src = sprite;
            image.alt = speciesName;
            image.width = 96;
            image.height = 96;

            image.addEventListener("error", () => {
                image.hidden = true;
            }, { once: true });

            card.appendChild(image);
        }

        const number = document.createElement("span");

        number.className = "pt-muted";

        number.textContent = "#" + String(pokemonId).padStart(3, "0");

        const name = document.createElement("strong");

        const genderSymbol = instance?.gender === "MALE" ? " ♂" : instance?.gender === "FEMALE" ? " ♀" : "";

        name.textContent = (instance?.nickname || speciesName) + genderSymbol;

        const details = document.createElement("span");
        details.className = "pt-card-note";

        if (instance) {
            details.textContent = [
                instance.nickname ? speciesName : null,
                instance.shiny ? "Shiny" : null,
                instance.currentlyOwned ? "Owned" : "Not currently owned"
            ]
                .filter(Boolean).join(" · ");
        } else {
            details.textContent = `Party slot ${slot}`;
        }

        card.append(number, name, details);

        if (!pokemon) {
            const note = document.createElement("span");
            note.className = "pt-card-note";
            note.textContent = "Name and sprite unavailable";

            card.appendChild(note);
        }

        if (!instance) {
            card.tabIndex = 0;
            card.setAttribute("role", "button");
            card.setAttribute(
                "aria-label",
                `Show details for ${speciesName}`
            );

            const selectPokemon = () => {window.location.href =
                `/pokedex.html?playthroughId=${playthroughId}` + `&pokemonId=${pokemonId}`;};

            card.addEventListener("click", selectPokemon);

            card.addEventListener("keydown", event => {
                if (event.key === "Enter" || event.key === " ") {event.preventDefault();selectPokemon();}});
        }

        return card;
    }

    async function loadPlaythrough() {

        const response = await authorizedFetch(apiPath);

        if (!response.ok) {
            throw new Error("Could not load this playthrough.");
        }

        const playthrough = await response.json();

        element("playthrough-name").textContent = playthrough.name;

        element("playthrough-game").textContent = "Pokémon " + displayName(playthrough.game.toLowerCase());

        document.title = `${playthrough.name} - PokéJourney`;

        element("connect-emulator-button").disabled = false;
    }

    async function createEmulatorConnection() {

        const button = element("connect-emulator-button");
        const message = element("connection-message");
        element("connection-panel").open = true;

        button.disabled = true;
        element("emulator-connection-info").hidden = true;
        message.textContent = "Creating connection command...";

        try {
            const response = await authorizedFetch(
                apiPath + "/emulator-connection",
                { method: "POST" }
            );

            if (!response.ok) {
                throw new Error("Could not create connection");
            }

            const connection = await response.json();

            element("emulator-connection-code").textContent = `connectPokeJourney(${JSON.stringify(connection.connectionCode)})`;

            element("copy-emulator-command").textContent = "Copy command";

            element("emulator-connection-info").hidden = false;

            message.textContent = "Command ready. Copy it and run it in mGBA.";

        } catch (error) {
            message.textContent = "Could not create a connection. Try again.";

            console.error(error);

        } finally {
            button.disabled = false;
        }
    }

    function formatLocationName(location, playerName, rivalName, mapId) {
        if (!location) {
            return `Unknown area (map ID: ${mapId})`;
        }

        const name = location.name
            .replace("{player}", () => playerName || "Player")
            .replace("{rival}", () => rivalName || "Rival");

        return location.area ? `${location.area} - ${name}` : name;
    }

    async function renderLiveParty(pokemonIds) {

        const partyKey = JSON.stringify(pokemonIds);

        if (partyKey === lastPartyKey) {
            return;
        }

        const ids = pokemonIds.slice(0, 6);
        const metadata = await Promise.all(ids.map(getPokemon));
        const cards = Array.from({ length: 6 }, (_, index) => index < ids.length
            ? createPokemonCard(ids[index], metadata[index], null, index + 1)
            : emptyPartySlot(index + 1));

        element("live-party").replaceChildren(...cards);

        // Retry missing metadata; successful results remain cached.
        lastPartyKey = metadata.every(Boolean) ? partyKey : null;
    }

    async function loadLiveGameState() {
        let response;

        try {
            response = await authorizedFetch(
                apiPath + "/game-state",
                { cache: "no-store" }
            );
        } catch (error) {
            setStatus("Connection unavailable", "error");
            console.error("Could not fetch live game state:", error);
            return;
        }

        if (response.status === 204) {
            setStatus("Waiting for emulator", "waiting");

            element("current-area").textContent = "Waiting for emulator data.";

            element("live-data-notice").textContent = "Connect mGBA to see your current party.";

            element("party-count").textContent = "(0/6)";

            lastPartyKey = null;

            await renderLiveParty([]);
            renderProgress(null);
            encounterDisplayKey = null;
            encounterDisplayVersion++;

            element("area-encounters").replaceChildren();

            element("area-encounters-status").textContent = "Waiting for area data.";
            return;
        }

        if (response.status === 404) {
            pollingStopped = true;
            setStatus("Playthrough not found", "error");
            return;
        }

        if (!response.ok) {
            setStatus("Could not load emulator data", "error");
            console.error("Game state HTTP status:", response.status);
            return;
        }

        try {
            const liveState = await response.json();
            const state = liveState.state;

            const receivedAt = new Date(liveState.receivedAt);

            const connected = Date.now() - receivedAt.getTime() < 15000;

            element("current-area").textContent = formatLocationName(
                    state.location, state.playerName, state.rivalName, state.mapId);

            const pokemonIds = Array.isArray(state.pokemonIds) ? state.pokemonIds.slice(0, 6) : [];

            element("party-count").textContent = `(${pokemonIds.length}/6)`;

            element("live-data-notice").textContent = connected
                ? pokemonIds.length === 0 ? "Party is empty." : "" : "Showing the last received data.";

            renderProgress(state);
            await renderAreaEncounters(state);
            await renderLiveParty(pokemonIds);

            setStatus(connected ? "Connected" : "Disconnected", connected ? "connected" : "disconnected");

        } catch (error) {
            setStatus("Could not display emulator data", "error");
            console.error("Could not display live game state:", error);
        }
    }

    async function pollLiveGameState() {

        if (pollingStopped) {
            return;
        }

        await loadLiveGameState();

        if (!pollingStopped) {
            pollTimer = setTimeout(pollLiveGameState, 2000);
        }
    }

    element("connect-emulator-button").addEventListener("click", createEmulatorConnection);

    element("copy-emulator-command").addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(element("emulator-connection-code").textContent);

            element("copy-emulator-command").textContent = "Copied!";

        } catch (error) {
            element("connection-message").textContent = "Copy failed. Select and copy the command manually.";
        }
    });

    window.addEventListener("pagehide", () => {
        pollingStopped = true;
        clearTimeout(pollTimer);
    });

    async function initialize() {

        try {
            await loadPlaythrough();

            await pollLiveGameState();

        } catch (error) {
            element("playthrough-name").textContent = "Playthrough unavailable";

            element("page-error").textContent = error.message;
            element("page-error").hidden = false;

            setStatus("Unavailable", "error");

            console.error(error);
        }
    }

    let encounterDisplayKey = null;
    let encounterDisplayVersion = 0;

    const encounterCache = new Map();

    function getAreaEncounters(mapId) {
        const cached = encounterCache.get(mapId);

        if (cached && Date.now() < cached.expiresAt) {
            return cached.promise;
        }

        const entry = {
            expiresAt: Date.now() + 60 * 60 * 1000
        };

        entry.promise = authorizedFetch(
            `${apiPath}/encounters?mapId=${encodeURIComponent(mapId)}`
        )
            .then(async response => {
                if (!response.ok) {
                    throw new Error("Could not load area encounters");
                }

                return response.json();
            })
            .catch(error => {
                // Wait before retrying a failed request.
                entry.expiresAt = Date.now() + 30000;
                throw error;
            });

        encounterCache.set(mapId, entry);

        return entry.promise;
    }

    function createSpeciesCard(id, pokemon, seen, caught) {
        const card = createPokemonCard(id, pokemon);

        card.classList.add("pt-species-card");

        const status = caught.has(id)
            ? "caught"
            : seen.has(id)
                ? "seen"
                : "unseen";

        card.dataset.pokedexStatus = status;

        const note = card.querySelector(".pt-card-note");

        if (note) {
            note.textContent = status === "caught"
                ? "Caught"
                : status === "seen"
                    ? "Seen"
                    : "Not seen";
        }

        if (status === "seen") {
            const eye = document.createElement("span");

            eye.className = "pt-seen-icon";
            eye.setAttribute("role", "img");
            eye.setAttribute("aria-label", "Seen, not caught");

            // Fixed SVG markup; no user input is inserted.
            eye.innerHTML = `
            <svg viewBox="0 0 24 24"
                 width="22"
                 height="22"
                 fill="none"
                 stroke="currentColor"
                 stroke-width="2"
                 aria-hidden="true">
                <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/>
                <circle cx="12" cy="12" r="3"/>
            </svg>
        `;

            card.appendChild(eye);
        }

        return card;
    }

    function encounterMethodName(method, locationKey) {

        if (method === "walk") {
            const tallGrassArea = /^route-\d+$/.test(locationKey || "") ||
                locationKey === "viridian-forest" || (locationKey || "").startsWith("safari-zone-");

            return tallGrassArea ? "Tall grass" : "Walking";
        }

        const names = {
            surf: "Surf", "old-rod": "Old Rod",
            "good-rod": "Good Rod", "super-rod": "Super Rod"
        };

        return names[method] || displayName(method);
    }

    async function renderAreaEncounters(state) {
        const key = JSON.stringify([
            state.mapId,
            state.locationKey,
            state.seenPokemonIds,
            state.caughtPokemonIds
        ]);

        if (key === encounterDisplayKey) {
            return;
        }

        encounterDisplayKey = key;
        const version = ++encounterDisplayVersion;

        const seen = new Set(state.seenPokemonIds || []);
        const caught = new Set(state.caughtPokemonIds || []);

        element("area-encounters-status").textContent =
            "Loading available Pokémon...";

        element("area-encounters").replaceChildren();

        try {
            const result = await getAreaEncounters(state.mapId);

            if (version !== encounterDisplayVersion) {
                return;
            }

            if (!result.mapped) {
                element("area-encounters-status").textContent =
                    "Encounter data has not been linked to this area yet.";
                return;
            }

            const metadata = await Promise.all(
                result.pokemon.map(entry =>
                    getPokemon(entry.pokemonId)
                )
            );

            if (version !== encounterDisplayVersion) {
                return;
            }

            const cards = result.pokemon.map((entry, index) => {
                const pokemon = metadata[index] || {
                    name: entry.name,
                    sprite: null
                };

                const card = createSpeciesCard(
                    entry.pokemonId,
                    pokemon,
                    seen,
                    caught
                );

                const groups = new Map();

                for (const option of entry.options) {
                    const conditions = [...option.conditions].sort();

                    const groupKey = JSON.stringify([
                        option.method,
                        conditions
                    ]);

                    if (!groups.has(groupKey)) {
                        groups.set(groupKey, {
                            method: option.method,
                            conditions,
                            levels: new Set()
                        });
                    }

                    const group = groups.get(groupKey);

                    for (
                        let level = option.minLevel;
                        level <= option.maxLevel;
                        level++
                    ) {
                        group.levels.add(level);
                    }
                }

                for (const group of groups.values()) {
                    const levels = [...group.levels].sort((a, b) => a - b);

                    const ranges = [];

                    for (let i = 0; i < levels.length; i++) {
                        const start = levels[i];
                        let end = start;

                        while (i + 1 < levels.length && levels[i + 1] === end + 1) {
                            end = levels[++i];
                        }

                        ranges.push(start === end ? String(start) : `${start}–${end}`);
                    }

                    const conditions = group.conditions.length > 0
                        ? " · " +
                        group.conditions.map(displayName).join(", ")
                        : "";

                    const line = document.createElement("span");
                    line.className = "pt-card-note";

                    line.textContent =
                        `${encounterMethodName(group.method, state.locationKey)}` +
                        ` · Lv. ${ranges.join(", ")}` +
                        conditions;

                    card.appendChild(line);
                }

                return card;
            });

            element("area-encounters").replaceChildren(...cards);

            element("area-encounters-status").textContent =
                cards.length === 0
                    ? "No encounters listed here for this game version."
                    : "Possible encounters. Some require items or game progress.";

            if (metadata.some(pokemon => !pokemon)) {
                encounterDisplayKey = null;
            }

        } catch (error) {
            if (version !== encounterDisplayVersion) {
                return;
            }

            encounterDisplayKey = null;

            element("area-encounters-status").textContent = "Could not load available Pokémon. Retrying shortly.";

            console.error("Encounter lookup failed:", error);
        }
    }

    renderLiveParty([]);
    renderProgress(null);
    element("open-pokedex").href = `/pokedex.html?playthroughId=${playthroughId}`;
    initialize();
})();
