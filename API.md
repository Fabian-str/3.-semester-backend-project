# PokéJourney API

## Pokémon

### GET /api/pokemon/{id}

Returns Pokémon data from PokéAPI.

Possible responses:

- `200 OK`
- `400 Bad Request`
- `404 Not Found`

---

## Playthroughs

### GET /api/playthroughs

Returns all playthroughs.

### GET /api/playthroughs/{id}

Returns a single playthrough.

Possible responses:

- `200 OK`
- `400 Bad Request`
- `404 Not Found`

### POST /api/playthroughs

Creates a new playthrough.

Example request:

```json
{
  "name": "My Pokémon Blue Run",
  "game": "BLUE"
}