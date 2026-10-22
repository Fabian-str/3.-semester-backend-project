local CONNECTION_CODE = ""
local PARTY_COUNT_ADDRESS = 0xD163
local PARTY_SPECIES_ADDRESS = 0xD164
local CURRENT_MAP_ADDRESS = 0xD35E
local PLAYER_NAME_ADDRESS = 0xD158
local RIVAL_NAME_ADDRESS = 0xD34A
local PARTY_DATA_ADDRESS = 0xD16B
local PARTY_POKEMON_SIZE = 44
local POKEDEX_CAUGHT_ADDRESS = 0xD2F7
local POKEDEX_SEEN_ADDRESS = 0xD30A
local POKEDEX_SPECIES_COUNT = 151

local frameCounter = 0
local lastState = ""

function connectPokeJourney(connectionCode)
    if type(connectionCode) ~= "string" or connectionCode == "" then
        console:log("Please provide a connection code.")
        return
    end

    CONNECTION_CODE = connectionCode
    lastState = ""

    console:log("Connection code set. Waiting to send game state.")
end

local function readName(address)
    local characters = {}

    for offset = 0, 6 do
        local value = emu:read8(address + offset)

        if value == 0x50 then
            break
        end

        if value >= 0x80 and value <= 0x99 then
            table.insert(characters, string.char(65 + value - 0x80))
        elseif value >= 0xA0 and value <= 0xB9 then
            table.insert(characters, string.char(97 + value - 0xA0))
        elseif value == 0x7F then
            table.insert(characters, " ")
        else
            table.insert(characters, "?")
        end
    end

    return table.concat(characters)
end

function readPlayerName()
    return readName(PLAYER_NAME_ADDRESS)
end

function readRivalName()
    return readName(RIVAL_NAME_ADDRESS)
end

local function sendGameState(mapId, partyCount, speciesIds)
    local client, err = socket.connect("127.0.0.1", 7071)

    if not client then
        console:log("Could not connect to backend: " .. tostring(err))
        return
    end

    local speciesJson = table.concat(speciesIds, ",")
    local playerName = readPlayerName()
    local rivalName = readRivalName()

    local body =
        '{"mapId":' .. mapId ..
        ',"partyCount":' .. partyCount ..
        ',"speciesIds":[' .. speciesJson .. ']' ..
        ',"playerName":"' .. playerName .. '"' ..
        ',"rivalName":"' .. rivalName .. '"}'

    local request =
        "POST /api/game-state HTTP/1.1\r\n" ..
        "Host: 127.0.0.1:7071\r\n" ..
        "X-Emulator-Code: " .. CONNECTION_CODE .. "\r\n" ..
        "Content-Type: application/json\r\n" ..
        "Content-Length: " .. #body .. "\r\n" ..
        "Connection: close\r\n" ..
        "\r\n" ..
        body

    local responseBuffer = ""
    local responseFinished = false
    local receivedCallbackId

    receivedCallbackId = client:add("received", function()
        if responseFinished then
            return
        end

        local data, receiveError = client:receive(4096)

        if not data or data == "" then
            responseFinished = true
            client:remove(receivedCallbackId)

            console:log("Connection ended before HTTP status was read: " ..tostring(receiveError))
            return
        end

        responseBuffer = responseBuffer .. data

        local statusLine = responseBuffer:match("^(.-)\r\n")

        if statusLine then
            responseFinished = true
            client:remove(receivedCallbackId)

            console:log("Backend response: " .. statusLine)
        end
    end)

    client:send(request)
    console:log("Sent game state: " .. body)
end

local function readParty()
    local count = emu:read8(PARTY_COUNT_ADDRESS)
    local species = {}

    for i = 0, count - 1 do
        local speciesId = emu:read8(PARTY_SPECIES_ADDRESS + i)
        table.insert(species, speciesId)
    end

    return count, species
end

local function readCurrentMap()
    return emu:read8(CURRENT_MAP_ADDRESS)
end

local function speciesToString(species)
    local result = ""

    for i, id in ipairs(species) do
        result = result .. id

        if i < #species then
            result = result .. ", "
        end
    end

    return result
end

local function buildStateString(mapId, partyCount, species)
    return
        "Map ID: " .. mapId ..
        " | Party count: " .. partyCount ..
        " | Species IDs: " .. speciesToString(species)
end

function inspectParty()
    local count = emu:read8(PARTY_COUNT_ADDRESS)

    if count > 6 then
        console:log("Invalid party count. Load your save first.")
        return
    end

    console:log("Party Pokémon: " .. count)

    for slot = 0, count - 1 do
        local address =
            PARTY_DATA_ADDRESS + slot * PARTY_POKEMON_SIZE

        local speciesId = emu:read8(address)
        local level = emu:read8(address + 0x21)

        local trainerId =
            emu:read8(address + 0x0C) * 256 +
            emu:read8(address + 0x0D)

        local dvs =
            emu:read8(address + 0x1B) * 256 +
            emu:read8(address + 0x1C)

        console:log(
            "Slot: " .. (slot + 1) ..
            " | Species ID: " .. speciesId ..
            " | Level: " .. level ..
            " | OT ID: " .. trainerId ..
            " | DVs: " .. string.format("%04X", dvs)
        )
    end
end

local function readPokedexFlags(address)
    local pokemonIds = {}

    for pokemonId = 1, POKEDEX_SPECIES_COUNT do
        local flagIndex = pokemonId - 1
        local byteOffset = math.floor(flagIndex / 8)
        local bitIndex = flagIndex % 8

        local value = emu:read8(address + byteOffset)

        local flagSet =
            math.floor(value / (2 ^ bitIndex)) % 2 == 1

        if flagSet then
            table.insert(pokemonIds, pokemonId)
        end
    end

    return pokemonIds
end

function inspectPokedex()
    local seenIds = readPokedexFlags(POKEDEX_SEEN_ADDRESS)
    local caughtIds = readPokedexFlags(POKEDEX_CAUGHT_ADDRESS)

    console:log(
        "Seen: " .. #seenIds ..
        " | Caught: " .. #caughtIds
    )

    console:log(
        "Seen National IDs: " .. table.concat(seenIds, ", ")
    )

    console:log(
        "Caught National IDs: " .. table.concat(caughtIds, ", ")
    )
end

callbacks:add("frame", function()
    frameCounter = frameCounter + 1

    if frameCounter % 30 ~= 0 then
        return
    end

    if CONNECTION_CODE == "" then
        return
    end

    local mapId = readCurrentMap()
    local partyCount, species = readParty()

    local currentState = buildStateString(
        mapId,
        partyCount,
        species
    )

    local stateChanged = currentState ~= lastState
    local heartbeatDue = frameCounter % 300 == 0

    if stateChanged or heartbeatDue then
        if stateChanged then
            console:log(currentState)
        end

        sendGameState(
            mapId,
            partyCount,
            species
        )

        lastState = currentState
    end
end)