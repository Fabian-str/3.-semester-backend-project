(() => {
    "use strict";

    const token = localStorage.getItem("token");

    if (!token) {
        window.location.replace("/");
        return;
    }

    const element = id => document.getElementById(id);
    const pokemonCache = new Map();

    let loadedPokemon = [];
    let pokemonLoadVersion = 0;

    element("username").textContent =
        localStorage.getItem("username") || "Unknown user";

    element("logout-button").addEventListener("click", () => {
        localStorage.removeItem("token");
        localStorage.removeItem("username");

        window.location.replace("/");
    });

    async function apiFetch(url, options = {}) {
        const headers = new Headers(options.headers);
        headers.set("Authorization", "Bearer " + token);

        const response = await fetch(url, {
            ...options,
            headers
        });

        if (response.status === 401) {
            localStorage.removeItem("token");
            localStorage.removeItem("username");

            window.location.replace("/");
            throw new Error("Your session has expired.");
        }

        if (!response.ok) {
            throw new Error(`Request failed (HTTP ${response.status}).`);
        }

        return response;
    }

    function showError(container, message, error) {
        container.textContent = message;
        console.error(message, error);
    }

    async function loadPlaythroughs() {
        const container = element("playthrough-list");
        container.textContent = "Loading...";

        try {
            const response = await apiFetch("/api/playthroughs");
            const playthroughs = await response.json();

            if (!Array.isArray(playthroughs)) {
                throw new Error("Expected a list of playthroughs.");
            }

            container.replaceChildren();

            if (playthroughs.length === 0) {
                container.textContent = "No playthroughs yet.";
                return;
            }

            for (const playthrough of playthroughs) {
                container.appendChild(createPlaythroughItem(playthrough));
            }
        } catch (error) {
            showError(
                container,
                "Could not load playthroughs. " + error.message,
                error
            );
        }
    }

    function createPlaythroughItem(playthrough) {
        const item = document.createElement("div");
        item.className = "playthrough-item";

        const info = document.createElement("a");
        info.href = "/playthrough.html?id=" +
            encodeURIComponent(playthrough.id);

        const name = document.createElement("strong");
        name.textContent = playthrough.name;

        const game = document.createElement("span");
        game.textContent = " — " + formatName(playthrough.game);

        info.append(name, game);

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.textContent = "Delete";

        deleteButton.addEventListener("click", async () => {
            if (!confirm(`Delete "${playthrough.name}"?`)) {
                return;
            }

            deleteButton.disabled = true;
            deleteButton.textContent = "Deleting...";

            try {
                await apiFetch(
                    "/api/playthroughs/" +
                    encodeURIComponent(playthrough.id),
                    { method: "DELETE" }
                );

                await Promise.all([
                    loadPlaythroughs(),
                    loadPokemon()
                ]);
            } catch (error) {
                deleteButton.disabled = false;
                deleteButton.textContent = "Delete";

                alert("Could not delete playthrough. " + error.message);
                console.error(error);
            }
        });

        item.append(info, deleteButton);

        return item;
    }

    async function getPokemon(id) {
        if (!pokemonCache.has(id)) {
            const request = apiFetch("/api/pokemon/" + id)
                .then(response => response.json())
                .catch(error => {
                    pokemonCache.delete(id);
                    throw error;
                });

            pokemonCache.set(id, request);
        }

        return pokemonCache.get(id);
    }

    async function loadPokemon() {
        const version = ++pokemonLoadVersion;
        const grid = element("pokemon-grid");

        loadedPokemon = [];
        grid.textContent = "Loading...";

        const detailsMessage = document.createElement("p");
        detailsMessage.textContent = "Select a Pokémon.";
        element("pokemon-details").replaceChildren(detailsMessage);

        try {
            const response = await apiFetch("/api/pokemon-instances");
            const instances = await response.json();

            if (version !== pokemonLoadVersion) {
                return;
            }

            if (!Array.isArray(instances)) {
                throw new Error("Expected a list of Pokémon.");
            }

            if (instances.length === 0) {
                grid.textContent = "No saved Pokémon yet.";
                return;
            }

            const results = [];

            // Load a few at a time and reuse species already requested.
            for (let start = 0; start < instances.length; start += 4) {
                const batch = await Promise.allSettled(
                    instances.slice(start, start + 4).map(async instance => ({
                        instance,
                        pokemon: await getPokemon(instance.pokemonId)
                    }))
                );

                if (version !== pokemonLoadVersion) {
                    return;
                }

                results.push(...batch);
            }

            loadedPokemon = results
                .filter(result => result.status === "fulfilled")
                .map(result => result.value);

            const failedCount = results.filter(
                result => result.status === "rejected"
            ).length;

            if (loadedPokemon.length === 0) {
                grid.textContent = "Could not load Pokémon information.";
                return;
            }

            renderPokemon();

            if (failedCount > 0) {
                const message = document.createElement("p");
                message.textContent =
                    `Could not load information for ${failedCount} Pokémon.`;

                grid.prepend(message);

                for (const result of results) {
                    if (result.status === "rejected") {
                        console.error(result.reason);
                    }
                }
            }
        } catch (error) {
            if (version !== pokemonLoadVersion) {
                return;
            }

            showError(
                grid,
                "Could not load Pokémon. " + error.message,
                error
            );
        }
    }

    function renderPokemon() {
        const grid = element("pokemon-grid");
        const sortBy = element("pokemon-sort").value;
        const generation = element("generation-filter").value;

        grid.replaceChildren();

        if (loadedPokemon.length === 0) {
            grid.textContent = "No saved Pokémon yet.";
            return;
        }

        const pokemon = loadedPokemon.filter(({ pokemon }) =>
            matchesGeneration(pokemon.id, generation)
        );

        if (pokemon.length === 0) {
            grid.textContent = "No Pokémon match this filter.";
            return;
        }

        pokemon.sort((a, b) => {
            if (sortBy === "name") {
                return getDisplayName(a).localeCompare(
                    getDisplayName(b),
                    undefined,
                    { sensitivity: "base" }
                );
            }

            return a.pokemon.id - b.pokemon.id;
        });

        grid.append(...pokemon.map(createPokemonCard));
    }

    function matchesGeneration(id, generation) {
        switch (generation) {
            case "1":
                return id >= 1 && id <= 151;
            case "2":
                return id >= 152 && id <= 251;
            case "3":
                return id >= 252 && id <= 386;
            default:
                return true;
        }
    }

    function getDisplayName({ instance, pokemon }) {
        return instance.nickname?.trim() || formatName(pokemon.name);
    }

    function createPokemonCard(data) {
        const { instance, pokemon } = data;
        const displayName = getDisplayName(data);
        const sprite = getPokemonSprite(pokemon, instance);

        const button = document.createElement("button");
        button.type = "button";
        button.className = "pokemon-box";

        button.classList.add(
            instance.shiny
                ? "pokemon-box-shiny"
                : instance.gender === "MALE"
                    ? "pokemon-box-male"
                    : instance.gender === "FEMALE"
                        ? "pokemon-box-female"
                        : "pokemon-box-default"
        );

        if (sprite) {
            const image = document.createElement("img");
            image.src = sprite;
            image.alt = displayName;
            image.loading = "lazy";

            image.addEventListener("error", () => {
                image.hidden = true;
            }, { once: true });

            button.appendChild(image);
        }

        const number = document.createElement("div");
        number.textContent =
            "#" + String(pokemon.id).padStart(3, "0");

        const name = document.createElement("strong");
        const genderSymbol =
            instance.gender === "MALE"
                ? "♂"
                : instance.gender === "FEMALE"
                    ? "♀"
                    : "";

        name.textContent =
            displayName + (genderSymbol ? " " + genderSymbol : "");

        button.append(number, name);

        button.addEventListener("click", () => {
            showPokemonDetails(data);
        });

        return button;
    }

    async function showPokemonDetails({ instance, pokemon }) {
        const container = element("pokemon-details");

        if (!window.PokemonDetails?.show) {
            container.textContent =
                "Pokémon details could not load. Check pokemon-details.js.";
            return;
        }

        try {
            await window.PokemonDetails.show(
                pokemon.id,
                container,
                { instance }
            );
        } catch (error) {
            showError(container, "Could not load Pokémon details.", error);
        }
    }

    function getPokemonSprite(pokemon, instance) {
        if (instance.shiny) {
            return instance.gender === "FEMALE"
                ? pokemon.shinyFemaleSprite ??
                pokemon.shinySprite ??
                pokemon.sprite
                : pokemon.shinySprite ?? pokemon.sprite;
        }

        return instance.gender === "FEMALE"
            ? pokemon.femaleSprite ?? pokemon.sprite
            : pokemon.sprite;
    }

    function formatName(value) {
        return String(value ?? "")
            .toLowerCase()
            .split(/[-_]/)
            .map(word =>
                word.charAt(0).toUpperCase() + word.slice(1)
            )
            .join(" ");
    }

    element("create-playthrough-form")
        .addEventListener("submit", async event => {
            event.preventDefault();

            const form = event.currentTarget;
            const button = form.querySelector('button[type="submit"]');
            const errorMessage = element("create-error");

            const name = element("playthrough-name").value.trim();
            const game = element("playthrough-game").value;

            errorMessage.textContent = "";

            if (!name || !game) {
                errorMessage.textContent = "Enter a name and select a game.";
                return;
            }

            button.disabled = true;

            try {
                await apiFetch("/api/playthroughs", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({ name, game })
                });

                form.reset();
                await loadPlaythroughs();
            } catch (error) {
                showError(
                    errorMessage,
                    "Could not create playthrough. " + error.message,
                    error
                );
            } finally {
                button.disabled = false;
            }
        });

    element("pokemon-sort")
        .addEventListener("change", renderPokemon);

    element("generation-filter")
        .addEventListener("change", renderPokemon);

    loadPlaythroughs();
    loadPokemon();
})();