package dev.empengines.chunkupkeep.client;

import dev.empengines.chunkupkeep.LedgerService;
import dev.empengines.chunkupkeep.economy.Bank;
import dev.empengines.chunkupkeep.net.LedgerActionPayload;
import dev.empengines.chunkupkeep.net.LedgerSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The Claim Ledger map. Left-click a free chunk to claim it, shift-left-click your
 * chunk to unclaim, right-click your chunk to toggle force loading. Arrows pan the view.
 */
public class LedgerScreen extends Screen {
    private static final int CELL = 12;
    private static final int[] COLOURS = {
            0xFF3A4A3A, // free
            0xFF4CAF50, // own
            0xFF2196F3, // own, force loaded
            0xFFC0392B, // other team
            0xFF202020  // out of reach
    };

    private LedgerSnapshotPayload snap;
    private int gridX, gridY, size;

    public LedgerScreen(LedgerSnapshotPayload snap) {
        super(Component.translatable("block.chunkupkeep.claim_ledger"));
        this.snap = snap;
    }

    public BlockPos ledger() { return snap.ledger(); }

    public void update(LedgerSnapshotPayload p) { this.snap = p; }

    @Override
    protected void init() {
        size = (snap.radius() * 2 + 1) * CELL;
        gridX = (width - size) / 2 - 70;
        gridY = (height - size) / 2;
        int bx = gridX + size + 12, by = gridY + size - 70;
        int step = 5;
        addRenderableWidget(Button.builder(Component.literal("↑"), b -> pan(0, -step)).bounds(bx + 22, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("←"), b -> pan(-step, 0)).bounds(bx, by + 22, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("•"), b -> recentre()).bounds(bx + 22, by + 22, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), b -> pan(step, 0)).bounds(bx + 44, by + 22, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b -> pan(0, step)).bounds(bx + 22, by + 44, 20, 20).build());
    }

    private void pan(int dx, int dz) {
        PacketDistributor.sendToServer(new LedgerActionPayload(snap.ledger(), 0, 0, -1, snap.viewX() + dx, snap.viewZ() + dz));
    }

    private void recentre() {
        ChunkPos c = new ChunkPos(snap.ledger());
        PacketDistributor.sendToServer(new LedgerActionPayload(snap.ledger(), 0, 0, -1, c.x, c.z));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int n = snap.radius() * 2 + 1;
        ChunkPos ledgerChunk = new ChunkPos(snap.ledger());
        g.fill(gridX - 2, gridY - 2, gridX + size + 2, gridY + size + 2, 0xFF555555);
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                byte state = snap.cells()[dz * n + dx];
                int x = gridX + dx * CELL, y = gridY + dz * CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, COLOURS[Math.max(0, Math.min(COLOURS.length - 1, state))]);
                int cx = snap.viewX() - snap.radius() + dx, cz = snap.viewZ() - snap.radius() + dz;
                if (cx == ledgerChunk.x && cz == ledgerChunk.z) g.fill(x + 4, y + 4, x + CELL - 5, y + CELL - 5, 0xFFFFFFFF);
            }
        }

        // Hover readout
        int[] hover = cellAt(mouseX, mouseY);
        if (hover != null) {
            int x = gridX + hover[0] * CELL, y = gridY + hover[1] * CELL;
            g.fill(x, y, x + CELL - 1, y + 1, 0xFFFFFFFF);
            g.fill(x, y + CELL - 2, x + CELL - 1, y + CELL - 1, 0xFFFFFFFF);
            int cx = snap.viewX() - snap.radius() + hover[0], cz = snap.viewZ() - snap.radius() + hover[1];
            g.drawString(font, "Chunk " + cx + ", " + cz, gridX, gridY + size + 6, 0xFFFFFF);
        }

        int tx = gridX + size + 12, ty = gridY;
        g.drawString(font, title, tx, ty, 0xFFFFFF);
        g.drawString(font, Component.translatable("chunkupkeep.screen.balance", Bank.format(snap.balance())), tx, ty + 14, 0xE0C060);
        g.drawString(font, Component.translatable("chunkupkeep.screen.claim_cost", Bank.format(snap.claimCost())), tx, ty + 28, 0xCCCCCC);
        g.drawString(font, Component.translatable("chunkupkeep.screen.rent", Bank.format(snap.rent())), tx, ty + 40, 0xCCCCCC);
        g.drawString(font, Component.translatable("chunkupkeep.screen.counts", snap.claimed(), snap.forceLoaded()), tx, ty + 52, 0xCCCCCC);
        legend(g, tx, ty + 70, COLOURS[0], "chunkupkeep.screen.free");
        legend(g, tx, ty + 82, COLOURS[1], "chunkupkeep.screen.own");
        legend(g, tx, ty + 94, COLOURS[2], "chunkupkeep.screen.loaded");
        legend(g, tx, ty + 106, COLOURS[3], "chunkupkeep.screen.other");
        legend(g, tx, ty + 118, COLOURS[4], "chunkupkeep.screen.far");
        g.drawString(font, Component.translatable("chunkupkeep.screen.help1"), gridX, gridY - 24, 0xAAAAAA);
        g.drawString(font, Component.translatable("chunkupkeep.screen.help2"), gridX, gridY - 13, 0xAAAAAA);
    }

    private void legend(GuiGraphics g, int x, int y, int colour, String key) {
        g.fill(x, y, x + 8, y + 8, colour);
        g.drawString(font, Component.translatable(key), x + 12, y, 0xCCCCCC);
    }

    private int[] cellAt(double mx, double my) {
        int n = snap.radius() * 2 + 1;
        int dx = (int) Math.floor((mx - gridX) / CELL), dz = (int) Math.floor((my - gridY) / CELL);
        if (mx < gridX || my < gridY || dx >= n || dz >= n) return null;
        return new int[]{dx, dz};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int[] cell = cellAt(mx, my);
        if (cell != null) {
            int n = snap.radius() * 2 + 1;
            byte state = snap.cells()[cell[1] * n + cell[0]];
            int cx = snap.viewX() - snap.radius() + cell[0], cz = snap.viewZ() - snap.radius() + cell[1];
            boolean own = state == LedgerService.OWN || state == LedgerService.OWN_LOADED;
            int action = -1;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hasShiftDown() && own) action = LedgerService.UNCLAIM;
            else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && state == LedgerService.FREE) action = LedgerService.CLAIM;
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && own) action = LedgerService.TOGGLE_LOAD;
            if (action >= 0) {
                PacketDistributor.sendToServer(new LedgerActionPayload(snap.ledger(), cx, cz, action, snap.viewX(), snap.viewZ()));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
