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

    function element(id) {
        return document.getElementById(id);
    }

    function setStatus(text, status) {
        element("emulator-status").textContent = text;
        element("emulator-status").dataset.status = status;
    }

    async function authorizedFetch(url, options = {}) {

        const response = await fetch(url, {
            ...options,
            headers: {
                ...options.headers,
                "Authorization": "Bearer " + token
            }
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

        return (value || "Unknown Pokémon")
            .split("-")
            .map(word =>
                word.charAt(0).toUpperCase() + word.slice(1)
            )
            .join(" ");
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

                console.error(
                    `Could not load Pokémon #${pokemonId}:`,
                    error
                );

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
            return instance.gender === "FEMALE"
                ? pokemon.shinyFemaleSprite
                ?? pokemon.shinySprite
                ?? pokemon.sprite
                : pokemon.shinySprite ?? pokemon.sprite;
        }

        return instance?.gender === "FEMALE"
            ? pokemon.femaleSprite ?? pokemon.sprite
            : pokemon.sprite;
    }

    function createPokemonCard(
        pokemonId,
        pokemon,
        instance = null,
        slot = null
    ) {
        const card = document.createElement("article");
        card.className = "pt-pokemon-card";
        card.dataset.shiny = String(Boolean(instance?.shiny));

        const speciesName = pokemon
            ? displayName(pokemon.name)
            : `Pokémon #${pokemonId}`;

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

        number.textContent =
            "#" + String(pokemonId).padStart(3, "0");

        const name = document.createElement("strong");

        const genderSymbol =
            instance?.gender === "MALE"
                ? " ♂"
                : instance?.gender === "FEMALE"
                    ? " ♀"
                    : "";

        name.textContent =
            (instance?.nickname || speciesName) + genderSymbol;

        const details = document.createElement("span");
        details.className = "pt-card-note";

        if (instance) {
            details.textContent = [
                instance.nickname ? speciesName : null,
                instance.shiny ? "Shiny" : null,
                instance.currentlyOwned
                    ? "Owned"
                    : "Not currently owned"
            ]
                .filter(Boolean)
                .join(" · ");
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

        return card;
    }

    async function loadPlaythrough() {

        const response = await authorizedFetch(apiPath);

        if (!response.ok) {
            throw new Error("Could not load this playthrough.");
        }

        const playthrough = await response.json();

        element("playthrough-name").textContent = playthrough.name;

        element("playthrough-game").textContent =
            "Pokémon " + displayName(playthrough.game.toLowerCase());

        document.title = `${playthrough.name} - PokéJourney`;

        element("connect-emulator-button").disabled = false;
        element("refresh-pokemon-button").disabled = false;
    }

    async function loadPokemon() {

        const button = element("refresh-pokemon-button");

        button.disabled = true;
        element("collection-status").textContent = "Loading collection...";

        try {
            const response =
                await authorizedFetch(apiPath + "/pokemon");

            if (!response.ok) {
                throw new Error("Could not load collection");
            }

            const instances = await response.json();

            const metadata = await Promise.all(
                instances.map(instance =>
                    getPokemon(instance.pokemonId)
                )
            );

            const cards = instances.map((instance, index) =>
                createPokemonCard(
                    instance.pokemonId,
                    metadata[index],
                    instance
                )
            );

            element("pokemon-list").replaceChildren(...cards);

            element("recorded-count").textContent = instances.length;

            element("owned-count").textContent =
                instances.filter(instance =>
                    instance.currentlyOwned
                ).length;

            element("species-count").textContent =
                new Set(
                    instances.map(instance => instance.pokemonId)
                ).size;

            element("collection-status").textContent =
                instances.length === 0
                    ? "No Pokémon recorded yet."
                    : "";

        } catch (error) {
            element("collection-status").textContent =
                "Could not refresh the collection. Try again.";

            console.error(error);

        } finally {
            button.disabled = false;
        }
    }

    async function createEmulatorConnection() {

        const button = element("connect-emulator-button");
        const message = element("connection-message");

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

            element("emulator-connection-code").textContent =
                `connectPokeJourney(${JSON.stringify(connection.connectionCode)})`;

            element("copy-emulator-command").textContent =
                "Copy command";

            element("emulator-connection-info").hidden = false;

            message.textContent =
                "Command ready. Copy it and run it in mGBA.";

        } catch (error) {
            message.textContent =
                "Could not create a connection. Try again.";

            console.error(error);

        } finally {
            button.disabled = false;
        }
    }

    function formatLocationName(
        location,
        playerName,
        rivalName,
        mapId
    ) {
        if (!location) {
            return `Unknown area (map ID: ${mapId})`;
        }

        const name = location.name
            .replace("{player}", () => playerName || "Player")
            .replace("{rival}", () => rivalName || "Rival");

        return location.area
            ? `${location.area} - ${name}`
            : name;
    }

    async function renderLiveParty(pokemonIds) {

        const partyKey = JSON.stringify(pokemonIds);

        if (partyKey === lastPartyKey) {
            return;
        }

        const metadata = await Promise.all(
            pokemonIds.map(getPokemon)
        );

        const cards = pokemonIds.map((id, index) =>
            createPokemonCard(id, metadata[index], null, index + 1)
        );

        element("live-party").replaceChildren(...cards);

        // Retry missing metadata; successful results remain cached.
        lastPartyKey = metadata.every(Boolean)
            ? partyKey
            : null;
    }

    async function loadLiveGameState() {

        try {
            const response = await authorizedFetch(
                apiPath + "/game-state",
                { cache: "no-store" }
            );

            if (response.status === 204) {
                setStatus("Waiting for emulator", "waiting");

                element("current-area").textContent =
                    "Waiting for emulator data.";

                element("trainer-info").textContent = "";

                element("emulator-last-update").textContent =
                    "No data received yet.";

                element("live-data-notice").textContent =
                    "Connect mGBA to see your current party.";

                element("party-count").textContent = "";
                element("live-party").replaceChildren();

                lastPartyKey = null;
                return;
            }

            if (response.status === 404) {
                pollingStopped = true;
                throw new Error("Playthrough not found");
            }

            if (!response.ok) {
                throw new Error("Could not load live data");
            }

            const liveState = await response.json();
            const state = liveState.state;

            const receivedAt = new Date(liveState.receivedAt);

            const connected =
                Date.now() - receivedAt.getTime() < 15000;

            setStatus(
                connected ? "Connected" : "Disconnected",
                connected ? "connected" : "disconnected"
            );

            element("current-area").textContent =
                formatLocationName(
                    state.location,
                    state.playerName,
                    state.rivalName,
                    state.mapId
                );

            element("trainer-info").textContent = [
                state.playerName
                    ? `Player: ${state.playerName}`
                    : null,
                state.rivalName
                    ? `Rival: ${state.rivalName}`
                    : null
            ]
                .filter(Boolean)
                .join(" · ");

            element("emulator-last-update").textContent =
                "Last received: " + receivedAt.toLocaleTimeString();

            const pokemonIds = state.pokemonIds || [];

            element("party-count").textContent =
                `(${pokemonIds.length}/6)`;

            element("live-data-notice").textContent =
                connected
                    ? pokemonIds.length === 0
                        ? "Party is empty."
                        : ""
                    : "Showing the last received area and party. No recent emulator data.";

            await renderLiveParty(pokemonIds);

        } catch (error) {
            setStatus("Connection unavailable", "error");

            element("live-data-notice").textContent =
                "Could not refresh live data. Any displayed data is from the last update.";

            console.error("Could not load live game state:", error);
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

    element("connect-emulator-button")
        .addEventListener("click", createEmulatorConnection);

    element("refresh-pokemon-button")
        .addEventListener("click", loadPokemon);

    element("copy-emulator-command")
        .addEventListener("click", async () => {
            try {
                await navigator.clipboard.writeText(
                    element("emulator-connection-code").textContent
                );

                element("copy-emulator-command").textContent =
                    "Copied!";

            } catch (error) {
                element("connection-message").textContent =
                    "Copy failed. Select and copy the command manually.";
            }
        });

    window.addEventListener("pagehide", () => {
        pollingStopped = true;
        clearTimeout(pollTimer);
    });

    async function initialize() {

        try {
            await loadPlaythrough();

            await Promise.all([
                loadPokemon(),
                pollLiveGameState()
            ]);

        } catch (error) {
            element("playthrough-name").textContent =
                "Playthrough unavailable";

            element("page-error").textContent = error.message;
            element("page-error").hidden = false;

            element("collection-status").textContent =
                "Collection unavailable.";

            setStatus("Unavailable", "error");

            console.error(error);
        }
    }

    initialize();
})();