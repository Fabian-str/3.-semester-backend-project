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
local BADGES_ADDRESS = 0xD356
local PLAYER_ID_ADDRESS = 0xD359
local PARTY_NICKNAMES_ADDRESS = 0xD2B5

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

local function readName(address, maxLength)
    local characters = {}

    for offset = 0, (maxLength or 7) - 1 do
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

local function readGameVersion()

    local title = emu:getGameTitle()
        :gsub("%z", "")
        :gsub("%s+$", "")
        :upper()

    if title == "POKEMON RED" then
        return "RED"
    end

    if title == "POKEMON BLUE" then
        return "BLUE"
    end

    return nil
end

local function readTrainerId()
    return
        emu:read8(PLAYER_ID_ADDRESS) * 256 +
        emu:read8(PLAYER_ID_ADDRESS + 1)
end

local function jsonString(value)
    local escaped = value
        :gsub("\\", "\\\\")
        :gsub('"', '\\"')
        :gsub("\r", "\\r")
        :gsub("\n", "\\n")
        :gsub("\t", "\\t")

    return '"' .. escaped .. '"'
end

local function readPartyJson(partyCount)
    local members = {}

    for slot = 0, partyCount - 1 do
        local address =
            PARTY_DATA_ADDRESS + slot * PARTY_POKEMON_SIZE

        local speciesId = emu:read8(address)
        local level = emu:read8(address + 0x21)

        local originalTrainerId =
            emu:read8(address + 0x0C) * 256 +
            emu:read8(address + 0x0D)

        local dvs =
            emu:read8(address + 0x1B) * 256 +
            emu:read8(address + 0x1C)

        local nickname = readName(
            PARTY_NICKNAMES_ADDRESS + slot * 11,
            10
        )

        members[#members + 1] =
            '{"speciesId":' .. speciesId ..
            ',"nickname":' .. jsonString(nickname) ..
            ',"level":' .. level ..
            ',"originalTrainerId":' .. originalTrainerId ..
            ',"dvs":' .. dvs ..
            '}'
    end

    return "[" .. table.concat(members, ",") .. "]"
end

local function readPCJson()
    local sram = emu.memory.sram

    if not sram or sram:size() < 32768 then
        return nil, "Full save memory is unavailable."
    end

    local currentBoxValue = emu:read8(0xD5A0)
    local currentBox = currentBoxValue % 128
    local initialized = currentBoxValue >= 128

    if currentBox > 11 then
        return nil, "Invalid active PC box."
    end

    local accessWasClosed = sram:read8(0x2598) == 0xFF

    if accessWasClosed then
        emu:write8(0x0000, 0x0A)
    end

    local success, result = pcall(function()
        if sram:read8(0x2598) == 0xFF then
            error("Save memory is unreadable.")
        end

        local pokemon = {}

        local function readBoxName(readByte, address)
            local characters = {}

            for offset = 0, 9 do
                local value = readByte(address + offset)

                if value == 0x50 then
                    break
                end

                if value >= 0x80 and value <= 0x99 then
                    characters[#characters + 1] =
                        string.char(65 + value - 0x80)

                elseif value >= 0xA0 and value <= 0xB9 then
                    characters[#characters + 1] =
                        string.char(97 + value - 0xA0)

                elseif value == 0x7F then
                    characters[#characters + 1] = " "

                else
                    characters[#characters + 1] = "?"
                end
            end

            return table.concat(characters)
        end

        for box = 0, 11 do
            local active = box == currentBox

            if active or initialized then
                local base
                local readByte

                if active then
                    base = 0xDA80

                    readByte = function(address)
                        return emu:read8(address)
                    end
                else
                    local bankStart =
                        box < 6 and 0x4000 or 0x6000

                    base = bankStart + (box % 6) * 0x462

                    readByte = function(address)
                        return sram:read8(address)
                    end
                end

                local count = readByte(base)

                if count > 20 then
                    error(
                        "Invalid Pokémon count in Box " ..
                        (box + 1)
                    )
                end

                for slot = 0, count - 1 do
                    local address = base + 22 + slot * 33

                    local speciesId = readByte(address)

                    if speciesId ~= readByte(base + 1 + slot) then
                        error(
                            "Inconsistent Pokémon data in Box " ..
                            (box + 1)
                        )
                    end

                    local originalTrainerId =
                        readByte(address + 0x0C) * 256 +
                        readByte(address + 0x0D)

                    local dvs =
                        readByte(address + 0x1B) * 256 +
                        readByte(address + 0x1C)

                    -- Header + 20 Pokémon structures + 20 OT names.
                    local nicknameAddress =
                        base + 22 + 20 * 33 + 20 * 11 +
                        slot * 11

                    local nickname = readBoxName(
                        readByte,
                        nicknameAddress
                    )

                    pokemon[#pokemon + 1] =
                        '{"speciesId":' .. speciesId ..
                        ',"nickname":' .. jsonString(nickname) ..
                        ',"originalTrainerId":' ..
                            originalTrainerId ..
                        ',"dvs":' .. dvs ..
                        '}'
                end
            end
        end

        return "[" .. table.concat(pokemon, ",") .. "]"
    end)

    if accessWasClosed then
        emu:write8(0x0000, 0x00)
    end

    if not success then
        return nil, tostring(result)
    end

    return result
end

local function readDayCareJson()
    local occupied = emu:read8(0xDA48)

    if occupied == 0 then
        return "[]"
    end

    if occupied ~= 1 then
        return nil, "Invalid Day Care status."
    end

    local address = 0xDA5F
    local speciesId = emu:read8(address)
    local nickname = readName(0xDA49, 10)

    local originalTrainerId =
        emu:read8(address + 0x0C) * 256 +
        emu:read8(address + 0x0D)

    local dvs =
        emu:read8(address + 0x1B) * 256 +
        emu:read8(address + 0x1C)

    return
        '[{"speciesId":' .. speciesId ..
        ',"nickname":' .. jsonString(nickname) ..
        ',"originalTrainerId":' .. originalTrainerId ..
        ',"dvs":' .. dvs ..
        '}]'
end

local function sendGameState(mapId,partyCount, speciesIds, seenIds,
    caughtIds, badgeMask, pcJson, partyJson, dayCareJson, currentState)

        local gameVersion = readGameVersion()

        if not gameVersion then
            console:log(
                "Unsupported ROM. Use the English Pokémon Red or Blue tracker setup."
            )

            CONNECTION_CODE = ""
            return
        end

        if partyCount < 1 or partyCount > 6 then
            console:log("Load your save with a valid party before connecting.")
            return
        end

        local trainerId = readTrainerId()

    local client, err = socket.connect("127.0.0.1", 7071)

    if not client then
        console:log("Could not connect to backend: " .. tostring(err))
        return
    end

    local speciesJson = table.concat(speciesIds, ",")
    local playerName = readPlayerName()
    local rivalName = readRivalName()

    -- Escape names before inserting them into JSON.
    local function escapeJson(value)
        return value
            :gsub("\\", "\\\\")
            :gsub('"', '\\"')
            :gsub("\r", "\\r")
            :gsub("\n", "\\n")
            :gsub("\t", "\\t")
    end

    local body =
        '{"mapId":' .. mapId ..
        ',"partyCount":' .. partyCount ..
        ',"speciesIds":[' .. speciesJson .. ']' ..
        ',"playerName":"' .. escapeJson(playerName) .. '"' ..
        ',"rivalName":"' .. escapeJson(rivalName) .. '"' ..
        ',"seenPokemonIds":[' .. table.concat(seenIds, ",") .. ']' ..
        ',"caughtPokemonIds":[' .. table.concat(caughtIds, ",") .. ']' ..
        ',"badgeMask":' .. badgeMask ..
        ',"gameVersion":"' .. gameVersion .. '"' ..
        ',"trainerId":' .. trainerId ..
        ',"party":' .. partyJson ..
        ',"pcPokemon":' .. pcJson ..
        ',"dayCarePokemon":' .. dayCareJson ..
        '}'

    local request =
        "POST /api/game-state HTTP/1.1\r\n" ..
        "Host: 127.0.0.1:7071\r\n" ..
        "X-Emulator-Code: " .. CONNECTION_CODE .. "\r\n" ..
        "Content-Type: application/json\r\n" ..
        "Content-Length: " .. #body .. "\r\n" ..
        "Connection: close\r\n" ..
        "\r\n" ..
        body

    local sentConnectionCode = CONNECTION_CODE

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

            local statusCode =
                tonumber(statusLine:match("^HTTP/%d+%.%d+%s+(%d+)"))

            -- Ignore responses from an older connection.
            if CONNECTION_CODE ~= sentConnectionCode then
                return
            end

            if statusCode == 200 then
                lastState = currentState

            elseif statusCode == 409 then
                CONNECTION_CODE = ""

                console:log(
                    "Connection rejected: wrong game version or another save."
                )

            elseif statusCode == 401 then
                CONNECTION_CODE = ""

                console:log(
                    "Connection code expired or invalid. Create a new command."
                )

            elseif statusCode == 400 then
                console:log(
                    "Backend rejected this update. " ..
                    "Connection retained; the tracker will retry. " ..
                    "See the IntelliJ console for the validation error."
                )

            else
                console:log(
                    "Update failed. The tracker will retry."
                )
            end
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

    if frameCounter % 30 ~= 0 or CONNECTION_CODE == "" then
        return
    end

    local mapId = readCurrentMap()
    local partyCount, species = readParty()

    if partyCount < 1 or partyCount > 6 then
        return
    end

    local seenIds = readPokedexFlags(POKEDEX_SEEN_ADDRESS)
    local caughtIds = readPokedexFlags(POKEDEX_CAUGHT_ADDRESS)
    local badgeMask = emu:read8(BADGES_ADDRESS)

    local pcJson, pcError = readPCJson()

    if not pcJson then
        if frameCounter % 300 == 0 then
            console:log(
                "Update blocked by PC read: " ..
                tostring(pcError)
            )
        end

        return
    end

    local dayCareJson, dayCareError = readDayCareJson()

    if not dayCareJson then
        if frameCounter % 300 == 0 then
            console:log(
                "Update blocked: " .. tostring(dayCareError)
            )
        end

        return
    end

    local partyJson = readPartyJson(partyCount)

    local currentState = table.concat({
        readGameVersion() or "UNKNOWN",
        tostring(readTrainerId()),
        tostring(mapId),
        tostring(partyCount),
        table.concat(species, ","),
        readPlayerName(),
        readRivalName(),
        table.concat(seenIds, ","),
        table.concat(caughtIds, ","),
        tostring(badgeMask),
        partyJson,
        pcJson,
        dayCareJson
    }, "|")

    local stateChanged = currentState ~= lastState
    local heartbeatDue = frameCounter % 300 == 0

    if not stateChanged and not heartbeatDue then
        return
    end

    if stateChanged then
        console:log(
            buildStateString(mapId, partyCount, species)
        )
    end

    sendGameState(
        mapId,
        partyCount,
        species,
        seenIds,
        caughtIds,
        badgeMask,
        pcJson,
        partyJson,
        dayCareJson,
        currentState
    )

    -- lastState is updated by the HTTP callback after a 200 response.
end)

function inspectPCContents()
    local sram = emu.memory.sram

    if not sram or sram:size() < 32768 then
        console:log("Full save memory is unavailable.")
        return
    end

    local currentBoxValue = emu:read8(0xD5A0)
    local currentBox = currentBoxValue % 128
    local boxesInitialized = currentBoxValue >= 128

    if currentBox > 11 then
        console:log("Invalid current box. Load your save first.")
        return
    end

    local total = 0

    for box = 0, 11 do
        local active = box == currentBox

        -- Before the first box change, unused SRAM boxes
        -- have not been initialized by the game.
        if not active and not boxesInitialized then
            console:log("Box " .. (box + 1) .. ": empty (unused)")
        else
            local base
            local readByte

            if active then
                base = 0xDA80

                readByte = function(address)
                    return emu:read8(address)
                end
            else
                local bankStart = box < 6 and 0x4000 or 0x6000
                base = bankStart + (box % 6) * 0x462

                readByte = function(address)
                    return sram:read8(address)
                end
            end

            local count = readByte(base)

            if count > 20 then
                console:log(
                    "Box " .. (box + 1) ..
                    ": invalid count " .. count ..
                    ". Inspection stopped."
                )
                return
            end

            console:log(
                "Box " .. (box + 1) ..
                (active and " (active)" or "") ..
                ": " .. count .. " Pokémon"
            )

            for slot = 0, count - 1 do
                -- Box Pokémon use 33-byte structures.
                local address = base + 22 + slot * 33

                local speciesId = readByte(address)

                local originalTrainerId =
                    readByte(address + 0x0C) * 256 +
                    readByte(address + 0x0D)

                local dvs =
                    readByte(address + 0x1B) * 256 +
                    readByte(address + 0x1C)

                console:log(
                    "  Slot: " .. (slot + 1) ..
                    " | Species ID: " .. speciesId ..
                    " | OT ID: " .. originalTrainerId ..
                    " | DVs: " .. string.format("%04X", dvs)
                )
            end

            total = total + count
        end
    end

    console:log("Total Pokémon in PC: " .. total)
end

function inspectPC()
    local sram = emu.memory.sram

    if not sram or sram:size() < 32768 then
        console:log("Full save memory is unavailable.")
        return
    end

    -- A saved player name cannot normally begin with 0xFF.
    local accessWasClosed = sram:read8(0x2598) == 0xFF

    if accessWasClosed then
        -- Open cartridge RAM access; this is a control register.
        emu:write8(0x0000, 0x0A)
    end

    local success, inspectionError = pcall(function()
        if sram:read8(0x2598) == 0xFF then
            error("Save memory is still unreadable.")
        end

        inspectPCContents()
    end)

    -- Restore closed access, even if inspection failed.
    if accessWasClosed then
        emu:write8(0x0000, 0x00)
    end

    if not success then
        console:log(
            "PC inspection failed: " .. tostring(inspectionError)
        )
    end
end