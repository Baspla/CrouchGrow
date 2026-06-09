# Crouch Grow

Crouch Grow is a Hytale server plugin that allows players to grow crops by crouching near them.

## How it works

When a player crouches near a crop a growth boost is applied to the crop, allowing it to grow faster than normal.
Everytime the player crouches 30s (configurable) are removed from the waiting time until the next growth stage.
This scales with growth multipliers, so fertilizing and watering the crop will make crouch growing even more effective.
You can use the `/crouchgrowinfo` to get a better idea of how it works.

## Configuration

The plugin can be configured in the `config.json` file:

-  `EnableCrouchGrowth` - Whether crouch growing is enabled or not. (default: true)

-  `GrowthRadius` - The radius around the player in which crops will be affected by crouch growing. (default: 5)

-  `SecondsAddedPerCrouch` - The amount of seconds added to the growth progress. (default: 30)

-  `MaxCropsAffectedPerCrouch` - The maximum number of crops that can be affected by crouch growing at the same time. (default: 16)

-  `ShowPlayerParticle` - Whether to show particles around the player when crouch growing. (default: true)

-  `ShowPlantParticle` - Whether to show particles around the plants when crouch growing. (default: true)

## Commands

- `/crouchgrowinfo` - Check if crouch growing is enabled for you. Requires the `crouchgrow.info` permission.

## Permissions

- `crouchgrow.info` - Allows players to use the `/crouchgrowinfo` command to check if crouch growing is enabled for them.