const token = localStorage.getItem("token");
const username = localStorage.getItem("username");

let loadedPokemon = [];

if (!token) {
    window.location.href = "/";
}

document.getElementById("username").textContent =
    username ?? "Unknown user";

document
    .getElementById("logout-button")
    .addEventListener("click", () => {

        localStorage.removeItem("token");
        localStorage.removeItem("username");

        window.location.href = "/";
    });

async function loadPlaythroughs() {

    const response = await fetch("/api/playthroughs", {
        headers: {
            "Authorization": "Bearer " + token
        }
    });

    if (!response.ok) {
        return;
    }

    const playthroughs = await response.json();

    const container =
        document.getElementById("playthrough-list");

    container.innerHTML = "";

    if (playthroughs.length === 0) {
        container.textContent =
            "No playthroughs yet.";
        return;
    }

    playthroughs.forEach(playthrough => {

        const item = document.createElement("div");
        item.className = "playthrough-item";

        const info = document.createElement("div");

        info.innerHTML = `
        <strong>${playthrough.name}</strong>
        <span>${playthrough.game}</span>
    `;

        info.addEventListener("click", () => {
            window.location.href =
                "/playthrough.html?id=" + playthrough.id;
        });

        const deleteButton =
            document.createElement("button");

        deleteButton.textContent = "Delete";

        deleteButton.addEventListener(
            "click",
            async event => {

                event.stopPropagation();

                const confirmed =
                    confirm(
                        `Delete "${playthrough.name}"?`
                    );

                if (!confirmed) {
                    return;
                }

                const response = await fetch(
                    "/api/playthroughs/" +
                    playthrough.id,
                    {
                        method: "DELETE",
                        headers: {
                            "Authorization":
                                "Bearer " + token
                        }
                    }
                );

                if (response.ok) {
                    loadPlaythroughs();
                    loadPokemon();
                }
            }
        );

        item.appendChild(info);
        item.appendChild(deleteButton);

        container.appendChild(item);
    });
}

async function loadPokemon() {

    const response = await fetch(
        "/api/pokemon-instances",
        {
            headers: {
                "Authorization": "Bearer " + token
            }
        }
    );

    if (!response.ok) {
        return;
    }

    const instances = await response.json();

    loadedPokemon = [];

    const grid = document.getElementById("pokemon-grid");

    grid.innerHTML = "";

    if (instances.length === 0) {
        grid.textContent = "No Pokémon caught yet.";
        return;
    }

    for (const instance of instances) {

        const pokemonResponse = await fetch("/api/pokemon/" + instance.pokemonId);

        if (!pokemonResponse.ok) {
            continue;
        }

        const pokemon = await pokemonResponse.json();

        const combined = {
            instance, pokemon
        };

        loadedPokemon.push(combined);
    }

    renderPokemon();
}

function renderPokemon() {

    const grid = document.getElementById("pokemon-grid");

    grid.innerHTML = "";

    if (loadedPokemon.length === 0) {
        grid.textContent = "No Pokémon caught yet.";
        return;
    }

    const sortBy = document.getElementById("pokemon-sort").value;

    const generation = document.getElementById("generation-filter").value;

    const sortedPokemon = loadedPokemon.filter(({ pokemon }) => {

        switch (generation) {
            case "1":
                return pokemon.id >= 1 && pokemon.id <= 151;
            case "2":
                return pokemon.id >= 152 && pokemon.id <= 251;
            case "3":
                return pokemon.id >= 252 && pokemon.id <= 386;
            default:
                return true;
        }
    });

    if (sortedPokemon.length === 0) {
        grid.textContent = "No Pokémon match this filter.";
        return;
    }

    sortedPokemon.sort((a, b) => {

        if (sortBy === "name") {
            const nameA = a.instance.nickname || a.pokemon.name;
            const nameB = b.instance.nickname || b.pokemon.name;

            return nameA.localeCompare(nameB);
        }

        return a.pokemon.id - b.pokemon.id;
    });

    for (const combined of sortedPokemon) {

        const { instance, pokemon } = combined;

        const displayName = instance.nickname ? instance.nickname : capitalize(pokemon.name);

        const genderSymbol = instance.gender === "MALE" ? "♂" : instance.gender === "FEMALE" ? "♀" : "";

        const box = document.createElement("div");

        box.className = "pokemon-box";

        box.classList.add(instance.shiny ? "pokemon-box-shiny" : instance.gender === "MALE" ? "pokemon-box-male" : instance.gender === "FEMALE" ? "pokemon-box-female" : "pokemon-box-default");

        box.innerHTML = `
            <img
                src="${getPokemonSprite(pokemon, instance)}"
                alt="${displayName}"
            >

            <div>
                #${String(pokemon.id).padStart(3, "0")}
            </div>

            <strong>
                ${displayName}
                ${genderSymbol}
            </strong>
        `;

        document
            .getElementById("generation-filter")
            .addEventListener("change", renderPokemon);

        box.addEventListener("click", () => {
            showPokemonDetails(combined);
        });

        grid.appendChild(box);
    }
}

function showPokemonDetails(data) {

    const instance = data.instance;
    const pokemon = data.pokemon;

    const details =
        document.getElementById("pokemon-details");

    details.innerHTML = `
        <img
            src="${getPokemonSprite(pokemon, instance)}"
            alt="${pokemon.name}"
        >

        <h2>${capitalize(pokemon.name)}</h2>

        <p>
            National Pokédex:
            #${String(pokemon.id).padStart(3, "0")}
        </p>

        <p>
            Nickname:
            ${instance.nickname ?? "None"}
        </p>

        <p>
            Gender:
            ${instance.gender ?? "Unknown"}
        </p>

        <p>
            Type:
            ${pokemon.types
        .map(capitalize)
        .join(" / ")}
        </p>

        <p>
            Shiny:
            ${instance.shiny ? "Yes" : "No"}
        </p>

        <p>
            Obtained:
            ${formatDate(instance.obtainedAt)}
        </p>
    `;
}

function formatDate(value) {

    if (!value) {
        return "Unknown";
    }

    return new Date(value)
        .toLocaleDateString();
}

document
    .getElementById("create-playthrough-form")
    .addEventListener("submit", async event => {

        event.preventDefault();

        const name =
            document.getElementById(
                "playthrough-name"
            ).value;

        const game =
            document.getElementById(
                "playthrough-game"
            ).value;

        const response = await fetch(
            "/api/playthroughs",
            {
                method: "POST",
                headers: {
                    "Authorization":
                        "Bearer " + token,
                    "Content-Type":
                        "application/json"
                },
                body: JSON.stringify({
                    name,
                    game
                })
            }
        );

        if (!response.ok) {
            document.getElementById(
                "create-error"
            ).textContent =
                "Could not create playthrough";

            return;
        }

        document.getElementById(
            "playthrough-name"
        ).value = "";

        document.getElementById(
            "playthrough-game"
        ).value = "";

        loadPlaythroughs();
    });

document
    .getElementById("pokemon-sort")
    .addEventListener("change", renderPokemon);

loadPlaythroughs();
loadPokemon();

function capitalize(value) {

    if (!value) {
        return "";
    }

    return value.charAt(0).toUpperCase()
        + value.slice(1);
}

function getPokemonSprite(pokemon, instance) {

    if (instance.shiny) {
        return instance.gender === "FEMALE"
            ? pokemon.shinyFemaleSprite ?? pokemon.shinySprite
            : pokemon.shinySprite;
    }

    return instance.gender === "FEMALE"
        ? pokemon.femaleSprite ?? pokemon.sprite
        : pokemon.sprite;
}