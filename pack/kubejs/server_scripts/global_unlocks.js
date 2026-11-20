// Server-wide unlocks (the End, later the Sky Gate). The Nether has no server-wide lock:
// each nether portal needs an Obsidian Key instead (emp_engines).
// A flag is stored in the server's persistent data. Players get the matching
// Chapters stage when the flag is set, and again whenever they log in, so
// offline players catch up.
//
//   /empyrean unlock <name>   set the flag (FTB Quests project rewards run this)
//   /empyrean lock <name>     clear it again (admin)
//   /empyrean status          list flags

const GLOBAL_UNLOCKS = {
  end: 'emp_engines:end_access'
}

function unlockKey(name) { return 'emp_engines_unlock_' + name }

function syncPlayer(server, player) {
  Object.keys(GLOBAL_UNLOCKS).forEach(name => {
    const stage = GLOBAL_UNLOCKS[name]
    const on = server.persistentData.getBoolean(unlockKey(name))
    server.runCommandSilent(`chapters ${on ? 'add' : 'remove'} ${player.username} ${stage}`)
  })
}

function setUnlock(server, name, on) {
  server.persistentData.putBoolean(unlockKey(name), on)
  server.players.forEach(p => syncPlayer(server, p))
  const label = name.charAt(0).toUpperCase() + name.slice(1)
  server.tell(on ? Text.gold(`The ${label} is now open to everyone.`) : Text.gray(`The ${label} has been sealed.`))
  console.info(`[Empyrean] Global unlock ${name} = ${on}`)
}

PlayerEvents.loggedIn(event => syncPlayer(event.server, event.player))

ServerEvents.commandRegistry(event => {
  const Commands = event.commands
  let unlock = Commands.literal('unlock')
  let lock = Commands.literal('lock')
  Object.keys(GLOBAL_UNLOCKS).forEach(name => {
    unlock = unlock.then(Commands.literal(name).executes(ctx => { setUnlock(ctx.source.server, name, true); return 1 }))
    lock = lock.then(Commands.literal(name).executes(ctx => { setUnlock(ctx.source.server, name, false); return 1 }))
  })
  const status = Commands.literal('status').executes(ctx => {
    const server = ctx.source.server
    Object.keys(GLOBAL_UNLOCKS).forEach(name => {
      const on = server.persistentData.getBoolean(unlockKey(name))
      ctx.source.sendSystemMessage(Text.of(`${name}: ${on ? 'open' : 'sealed'}`))
    })
    return 1
  })
  event.register(Commands.literal('empyrean').requires(src => src.hasPermission(2))
    .then(unlock).then(lock).then(status))
})
