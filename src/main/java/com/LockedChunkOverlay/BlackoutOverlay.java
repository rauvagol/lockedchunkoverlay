package com.LockedChunkOverlay;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FontMetrics;
import net.runelite.client.ui.FontManager;

class BlackoutOverlay extends Overlay
{
	private final Client client;

	private int alpha = 0; // 0..255
	private boolean showText = false;

	@Inject
	BlackoutOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		// Draw beneath UI widgets but above the 3D scene and most overlays
		setLayer(OverlayLayer.UNDER_WIDGETS);
		setPriority(OverlayPriority.HIGHEST);
	}

	void setState(int alpha, boolean showText)
	{
		this.alpha = Math.max(0, Math.min(255, alpha));
		this.showText = showText;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int x = client.getViewportXOffset();
		int y = client.getViewportYOffset();
		int w = client.getViewportWidth();
		int h = client.getViewportHeight();

		// Fallback in case viewport metrics are unavailable
		if (w <= 0 || h <= 0)
		{
			x = 0;
			y = 0;
			w = client.getCanvasWidth();
			h = client.getCanvasHeight();
		}
		Color prev = graphics.getColor();
		graphics.setColor(new Color(0, 0, 0, alpha));
		graphics.fillRect(x, y, w, h);

		if (showText)
		{
			// Draw centered yellow message with RuneScape font
			final String msg = "You have been eaten by a grue.";
			Font prevFont = graphics.getFont();
			Font base = FontManager.getRunescapeBoldFont().deriveFont(16f);
			graphics.setFont(base);
			FontMetrics fm0 = graphics.getFontMetrics();
			int w0 = Math.max(1, fm0.stringWidth(msg));
			int targetW = Math.max(1, (int)(w * 0.5));
			float size = 16f * ((float) targetW / (float) w0);
			Font rsFont = base.deriveFont(size);
			graphics.setFont(rsFont);
			graphics.setColor(Color.YELLOW);
			FontMetrics fm = graphics.getFontMetrics();
			int textW = fm.stringWidth(msg);
			int textH = fm.getAscent();
			int drawX = x + (w - textW) / 2;
			int drawY = y + (h + textH) / 2; // baseline-centered
			graphics.drawString(msg, drawX, drawY);

			graphics.setFont(prevFont);
		}

		graphics.setColor(prev);
		return null;
	}
}


