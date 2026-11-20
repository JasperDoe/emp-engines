// Prints the world settings the pack relies on, so the boot test can
// confirm the datapack overrides took effect. Look for "[Empyrean]" lines.
ServerEvents.loaded(event => {
  const ow = event.server.overworld()
  console.info(`[Empyrean] Overworld build height: ${ow.getMinBuildHeight()} to ${ow.getMaxBuildHeight()} (expect -64 to 448)`)
  event.server.getAllLevels().forEach(level => {
    // KubeJS gives the dimension as an id ("minecraft:the_nether"); a raw key prints with the id inside it
    const dim = String(level.dimension)
    if (dim.indexOf('minecraft:the_nether') >= 0)
      console.info(`[Empyrean] Nether build height: ${level.getMinBuildHeight()} to ${level.getMaxBuildHeight()} (Amplified Nether sets this)`)
  })
  try {
    const QuestFile = Java.loadClass('dev.ftb.mods.ftbquests.quest.ServerQuestFile')
    const file = QuestFile.INSTANCE
    console.info(`[Empyrean] FTB Quests: ${file.getAllChapters().size()} chapters (expect 11)`)
  } catch (e) {
    console.warn(`[Empyrean] FTB Quests check failed: ${e}`)
  }
})
