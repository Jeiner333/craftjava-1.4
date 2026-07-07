package com.miguel.craft;

import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class HudRenderSystem {

    public void render(World world, int width, int height, Block[] hotbar, int hotbarIndex, Map<Block, Integer> inventory, float health, MiningState miningState) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        int slotSize = 48;
        int padding = 6;
        int totalWidth = hotbar.length * (slotSize + padding) - padding;
        int startX = (width - totalWidth) / 2;
        int hotbarY = height - slotSize - 20;

        // barra de vida arriba de la hotbar
        int barWidth = 200, barHeight = 14;
        int barX = (width - barWidth) / 2;
        int barY = hotbarY - barHeight - 10;
        glColor4f(0.15f, 0.15f, 0.15f, 0.6f);
        drawQuad(barX, barY, barWidth, barHeight);
        float healthFrac = health / Player.MAX_HEALTH;
        glColor3f(0.85f - 0.4f * healthFrac, 0.15f + 0.6f * healthFrac, 0.15f);
        drawQuad(barX + 2, barY + 2, (barWidth - 4) * healthFrac, barHeight - 4);

        for (int i = 0; i < hotbar.length; i++) {
            int x = startX + i * (slotSize + padding);
            boolean selected = i == hotbarIndex;

            glColor4f(0.1f, 0.1f, 0.1f, 0.55f);
            drawQuad(x, hotbarY, slotSize, slotSize);

            if (selected) glColor3f(1f, 1f, 1f); else glColor3f(0.4f, 0.4f, 0.4f);
            drawQuadOutline(x, hotbarY, slotSize, slotSize, selected ? 3 : 1);

            Block b = hotbar[i];
            int count = inventory.getOrDefault(b, 0);
            float alpha = count > 0 ? 1f : 0.25f;
            glColor4f(b.r, b.g, b.b, alpha);
            int m = 8;
            drawQuad(x + m, hotbarY + m, slotSize - 2 * m, slotSize - 2 * m);

            if (count > 0) {
                float frac = Math.min(1f, count / (float) Block.MAX_STACK);
                glColor3f(1f, 1f, 1f);
                drawQuad(x + 4, hotbarY + slotSize - 6, (slotSize - 8) * frac, 3);
            }
        }

        if (miningState != null) {
            Block target = world.getBlock(miningState.x, miningState.y, miningState.z);
            float frac = Math.min(1f, miningState.progress / Math.max(target.hardness, 0.05f));
            int mw = 120, mh = 8;
            int mx = (width - mw) / 2, my = hotbarY - barHeight - 30;
            glColor4f(0.1f, 0.1f, 0.1f, 0.6f);
            drawQuad(mx, my, mw, mh);
            glColor3f(0.9f, 0.9f, 0.2f);
            drawQuad(mx + 1, my + 1, (mw - 2) * frac, mh - 2);
        }

        glColor3f(1f, 1f, 1f);
        int cx = width / 2, cy = height / 2, crossSize = 8;
        drawQuad(cx - crossSize / 2f, cy - 1, crossSize, 2);
        drawQuad(cx - 1, cy - crossSize / 2f, 2, crossSize);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glDisable(GL_BLEND);
    }

    private void drawQuad(float x, float y, float w, float h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private void drawQuadOutline(float x, float y, float w, float h, float thickness) {
        glLineWidth(thickness);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }
}
