// Skybound SMP: no building or breaking at or above Y = BUILD_LIMIT.
// Players must fly to anything up there, and can't dig or pillar into sky
// structures. Creative and spectator players are exempt (for admins).
// Interacting (chests, doors, levers) still works.
const BUILD_LIMIT = 250

function blockedAt(event) {
  const player = event.player
  if (!player || player.isCreative() || player.isSpectator()) return false
  return event.block.pos.y >= BUILD_LIMIT
}

BlockEvents.placed(event => {
  if (!blockedAt(event)) return
  event.player.displayClientMessage(Text.red(`You can't build at or above Y ${BUILD_LIMIT}.`), true)
  event.cancel()
})

BlockEvents.broken(event => {
  if (!blockedAt(event)) return
  event.player.displayClientMessage(Text.red(`You can't break blocks at or above Y ${BUILD_LIMIT}.`), true)
  event.cancel()
})
