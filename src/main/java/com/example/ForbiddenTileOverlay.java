package com.example;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.api.Perspective;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class ForbiddenTileOverlay extends Overlay
{
	private final Client client;
	private final Set<WorldPoint> tiles = new HashSet<>();

	@Inject
	ForbiddenTileOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
	}

	void setTiles(Set<WorldPoint> worldPoints)
	{
		tiles.clear();
		if (worldPoints != null)
		{
			tiles.addAll(worldPoints);
		}
	}

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (tiles.isEmpty())
        {
            return null;
        }

        final int plane = client.getPlane();
        final Color prev = graphics.getColor();
        final Color fill = new Color(0, 0, 0, 180);

        for (WorldPoint wp : tiles)
        {
            if (wp.getPlane() != plane)
            {
                continue;
            }
            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null)
            {
                continue;
            }
            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null)
            {
                graphics.setColor(fill);
                graphics.fillPolygon(poly);
            }
        }

        graphics.setColor(prev);
        return null;
    }
}


