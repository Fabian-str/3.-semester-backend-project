(() => {
    "use strict";

    const $ = id => document.getElementById(id);
    const token = localStorage.getItem("token");
    const params = new URLSearchParams(window.location.search);

    const pageSize = 24;
    const cache = new Map();

    const scope = $("pokedex-playthrough");
    const view = $("pokedex-view");
    const filter = $("pokedex-filter");

    let playthroughs = [];
    let seen = new Set();
    let caught = new Set();
    let owned = [];

    let page = 0;
    let version = 0;
    let scopeVersion = 0;
    let ready = false;

    let selectedId = Number(params.get("pokemonId")) || null;

    const pretty = value => String(value || "")
        .split("-")
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");

    async function authJson(path) {
        const response = await fetch(path, {
            headers: {
                Authorization: "Bearer " + token
            },
            cache: "no-store"
        });

        if (response.status === 401) {
            localStorage.removeItem("token");
            window.location.replace("/");
            throw new Error("Session expired");
        }

        if (!response.ok) {
            throw new Error(`Request failed: HTTP ${response.status}`);
        }

        return response.status === 204
            ? null
            : response.json();
    }

    function metadata(id) {
        if (!cache.has(id)) {
            const entry = { value: null };

            entry.promise = fetch(`/api/pokemon/${id}`)
                .then(async response => {
                    if (!response.ok) {
                        throw new Error(`Could not load Pokémon #${id}`);
                    }

                    entry.value = await response.json();
                    return entry.value;
                })
                .catch(error => {
                    cache.delete(id);
                    throw error;
                });

            cache.set(id, entry);
        }

        return cache.get(id).promise;
    }

    function currentGame() {
        return playthroughs.find(
            item => String(item.id) === scope.value
        )?.game;
    }

    function showDetails(
        id,
        instance = null,
        game = currentGame()
    ) {
        selectedId = id;

        PokemonDetails.show(
            id,
            $("pokemon-details"),
            { instance, game }
        );

        const url = new URL(window.location.href);
        url.searchParams.set("pokemonId", id);
        window.history.replaceState(null, "", url);
    }

    function createCard(id, pokemon, record) {
        const button = document.createElement("button");

        button.type = "button";
        button.className = "pokemon-box pd-card";

        const instance = record?.instance;

        const image = document.createElement("img");

        image.src = instance?.shiny
            ? (instance.gender === "FEMALE"
            ? pokemon.shinyFemaleSprite
            : null) ?? pokemon.shinySprite ?? pokemon.sprite
            : (instance?.gender === "FEMALE"
            ? pokemon.femaleSprite
            : null) ?? pokemon.sprite;

        image.alt = pretty(pokemon.name);

        image.addEventListener("error", () => {
            image.hidden = true;
        }, { once: true });

        const number = document.createElement("div");

        number.textContent =
            "#" + String(id).padStart(3, "0");

        const name = document.createElement("strong");

        name.textContent =
            instance?.nickname || pretty(pokemon.name);

        const note = document.createElement("span");

        if (record) {
            note.textContent =
                `${record.playthrough.name} · ` +
                (instance.currentlyOwned
                    ? "Owned"
                    : "Not currently owned");

            button.classList.add(
                instance.shiny
                    ? "pokemon-box-shiny"
                    : instance.gender === "MALE"
                        ? "pokemon-box-male"
                        : instance.gender === "FEMALE"
                            ? "pokemon-box-female"
                            : "pokemon-box-default"
            );

        } else if (scope.value !== "general") {
            const state = caught.has(id)
                ? "caught"
                : seen.has(id)
                    ? "seen"
                    : "unknown";

            button.dataset.pokedexStatus = state;

            note.textContent = state === "caught"
                ? "Caught"
                : state === "seen"
                    ? "Seen"
                    : "No recorded sighting";

            if (state === "seen") {
                const eye = document.createElement("span");

                eye.className = "pd-eye";
                eye.setAttribute("role", "img");
                eye.setAttribute("aria-label", "Seen, not caught");

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

                button.appendChild(eye);
            }
        }

        button.append(image, number, name, note);

        button.addEventListener("click", () => {
            showDetails(
                id,
                instance,
                record?.playthrough.game || currentGame()
            );
        });

        return button;
    }

    async function renderPage() {
        if (!ready) {
            return;
        }

        const request = ++version;
        const individual = view.value === "owned";

        let items = individual
            ? [...owned]
            : Array.from({ length: 151 }, (_, i) => i + 1);

        if (!individual && scope.value !== "general") {
            if (filter.value === "caught") {
                items = items.filter(id => caught.has(id));
            }

            if (filter.value === "seen") {
                items = items.filter(
                    id => seen.has(id) && !caught.has(id)
                );
            }
        }

        const query = $("pokedex-search")
            .value.trim().toLowerCase();

        if (query) {
            items = items.filter(item => {
                const id = individual
                    ? item.instance.pokemonId
                    : item;

                const pokemon = cache.get(id)?.value;

                return String(id) === query ||
                    String(id).padStart(3, "0") === query ||
                    pokemon?.name?.toLowerCase() === query ||
                    (
                        pokemon &&
                        pretty(pokemon.name).toLowerCase() === query
                    ) ||
                    (
                        individual &&
                        item.instance.nickname?.toLowerCase() === query
                    );
            });
        }

        const pageCount = Math.max(
            1,
            Math.ceil(items.length / pageSize)
        );

        page = Math.min(page, pageCount - 1);

        const slice = items.slice(
            page * pageSize,
            (page + 1) * pageSize
        );

        $("pokedex-grid").replaceChildren();
        $("previous-page").disabled = true;
        $("next-page").disabled = true;
        $("pokedex-status").textContent = "Loading Pokémon...";

        try {
            const cards = [];

            for (let start = 0; start < slice.length; start += 4) {
                const batch = await Promise.all(
                    slice.slice(start, start + 4).map(async item => {
                        const id = individual
                            ? item.instance.pokemonId
                            : item;

                        return createCard(
                            id,
                            await metadata(id),
                            individual ? item : null
                        );
                    })
                );

                if (request !== version) {
                    return;
                }

                cards.push(...batch);
            }

            $("pokedex-grid").replaceChildren(...cards);

            $("pokedex-status").textContent = cards.length
                ? ""
                : query
                    ? "No match. Search by number, nickname, or a species name from an already loaded page."
                    : individual
                        ? "No individual Pokémon saved yet. Automatic import is not implemented yet."
                        : "No species match this filter.";

        } catch (error) {
            if (request === version) {
                $("pokedex-status").textContent =
                    "Could not load Pokémon. Change page or submit the search to retry.";
            }

            console.error(error);

        } finally {
            if (request === version) {
                $("previous-page").disabled = page === 0;

                $("next-page").disabled =
                    page + 1 >= pageCount;

                $("page-number").textContent =
                    `Page ${page + 1} of ${pageCount}`;
            }
        }
    }

    async function loadScope() {
        const request = ++scopeVersion;

        ++version;
        ready = false;
        page = 0;

        seen = new Set();
        caught = new Set();
        owned = [];

        $("pokedex-grid").replaceChildren();

        $("pokedex-status").textContent =
            "Loading playthrough data...";

        $("pokedex-context").textContent = "";
        $("previous-page").disabled = true;
        $("next-page").disabled = true;

        const general = scope.value === "general";

        view.disabled = general;

        if (general) {
            view.value = "species";
            filter.value = "all";
        }

        filter.disabled =
            general || view.value === "owned";

        const url = new URL(window.location.href);

        url.searchParams.delete("playthroughId");
        url.searchParams.delete("scope");

        if (scope.value === "all") {
            url.searchParams.set("scope", "all");

        } else if (!general) {
            url.searchParams.set(
                "playthroughId",
                scope.value
            );
        }

        window.history.replaceState(null, "", url);

        try {
            if (!general) {
                const selected = scope.value === "all"
                    ? playthroughs
                    : playthroughs.filter(
                        item => String(item.id) === scope.value
                    );

                const records = [];

                for (
                    let start = 0;
                    start < selected.length;
                    start += 4
                ) {
                    const batch = await Promise.all(
                        selected.slice(start, start + 4)
                            .map(async playthrough => {
                                const [snapshot, instances] =
                                    await Promise.all([
                                        authJson(
                                            `/api/playthroughs/${playthrough.id}/game-state`
                                        ),
                                        authJson(
                                            `/api/playthroughs/${playthrough.id}/pokemon`
                                        )
                                    ]);

                                return {
                                    playthrough,
                                    state: snapshot?.state,
                                    instances: instances || []
                                };
                            })
                    );

                    if (request !== scopeVersion) {
                        return;
                    }

                    records.push(...batch);
                }

                const seenIds = [];
                const caughtIds = [];
                const instances = [];

                let missing = 0;

                for (const record of records) {
                    if (
                        !Array.isArray(record.state?.seenPokemonIds) ||
                        !Array.isArray(record.state?.caughtPokemonIds)
                    ) {
                        missing++;
                    }

                    seenIds.push(
                        ...(record.state?.seenPokemonIds || [])
                    );

                    caughtIds.push(
                        ...(record.state?.caughtPokemonIds || [])
                    );

                    instances.push(
                        ...record.instances
                            .filter(item =>
                                item.pokemonId >= 1 &&
                                item.pokemonId <= 151
                            )
                            .map(instance => ({
                                instance,
                                playthrough: record.playthrough
                            }))
                    );
                }

                const validId = id =>
                    Number.isInteger(id) &&
                    id >= 1 && id <= 151;

                seen = new Set(seenIds.filter(validId));
                caught = new Set(caughtIds.filter(validId));

                owned = instances.sort((a, b) =>
                    a.instance.pokemonId - b.instance.pokemonId
                );

                $("pokedex-context").textContent =
                    `${seen.size} recorded seen · ` +
                    `${caught.size} recorded caught · ` +
                    `${owned.length} saved individuals.` +
                    (
                        missing
                            ? ` ${missing} playthrough(s) have no complete Pokédex snapshot.`
                            : ""
                    );

            } else {
                $("pokedex-context").textContent =
                    "General reference. Select a playthrough to see your recorded progress.";
            }

            if (request !== scopeVersion) {
                return;
            }

            ready = true;

            if (selectedId >= 1 && selectedId <= 151) {
                showDetails(selectedId);
            }

            await renderPage();

        } catch (error) {
            if (request === scopeVersion) {
                $("pokedex-status").textContent =
                    "Could not load playthrough data. Select another option or reload to retry.";
            }

            console.error(error);
        }
    }

    scope.addEventListener("change", loadScope);

    for (const control of [view, filter]) {
        control.addEventListener("change", () => {
            page = 0;

            filter.disabled =
                scope.value === "general" ||
                view.value === "owned";

            renderPage();
        });
    }

    $("pokedex-search-form").addEventListener("submit", event => {
        event.preventDefault();
        page = 0;
        renderPage();
    });

    $("previous-page").addEventListener("click", () => {
        if (page > 0) {
            page--;
            renderPage();
        }
    });

    $("next-page").addEventListener("click", () => {
        page++;
        renderPage();
    });

    async function initialize() {
        if (token) {
            try {
                playthroughs = (
                    await authJson("/api/playthroughs")
                ).filter(item =>
                    ["RED", "BLUE"].includes(item.game)
                );

                const all = document.createElement("option");

                all.value = "all";
                all.textContent = "All Red / Blue playthroughs";

                scope.appendChild(all);

                for (const item of playthroughs) {
                    const option =
                        document.createElement("option");

                    option.value = String(item.id);

                    option.textContent =
                        `${item.name} (${pretty(item.game.toLowerCase())})`;

                    scope.appendChild(option);
                }

                const requested = params.get("playthroughId");

                if (
                    requested &&
                    !playthroughs.some(item =>
                        String(item.id) === requested
                    )
                ) {
                    throw new Error(
                        "Requested playthrough is unavailable or unsupported"
                    );
                }

                scope.value = requested ||
                    (
                        params.get("scope") === "all"
                            ? "all"
                            : "general"
                    );

            } catch (error) {
                $("pokedex-status").textContent =
                    "Could not load your playthroughs. Your progress has not been loaded. Reload to retry.";

                console.error(error);
                return;
            }

        } else if (params.has("playthroughId") || params.get("scope") === "all") {

            window.location.replace("/");
            return;
        }

        await loadScope();
    }

    initialize();
})();