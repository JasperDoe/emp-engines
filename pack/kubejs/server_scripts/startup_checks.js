// Prints the world settings the pack relies on, so the boot test can
// confirm the datapack overrides took effect. Look for "[Skybound]" lines.
ServerEvents.loaded(event => {
  const ow = event.server.overworld()
  console.info(`[Skybound] Overworld build height: ${ow.getMinBuildHeight()} to ${ow.getMaxBuildHeight()} (expect -64 to 448)`)
  event.server.getAllLevels().forEach(level => {
    if (String(level.dimension().location()) == 'minecraft:the_nether')
      console.info(`[Skybound] Nether coordinate scale: ${level.dimensionType().coordinateScale()} (expect 1.0)`)
  })
  try {
    const QuestFile = Java.loadClass('dev.ftb.mods.ftbquests.quest.ServerQuestFile')
    const file = QuestFile.INSTANCE
    console.info(`[Skybound] FTB Quests: ${file.getAllChapters().size()} chapters (expect 11)`)
  } catch (e) {
    console.warn(`[Skybound] FTB Quests check failed: ${e}`)
  }
})
