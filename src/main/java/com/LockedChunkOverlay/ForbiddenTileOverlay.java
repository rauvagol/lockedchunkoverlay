package com.LockedChunkOverlay;

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
import java.awt.BasicStroke;
import java.awt.geom.GeneralPath;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

class ForbiddenTileOverlay extends Overlay
{
	private final Client client;
    private final LockedChunkOverlayConfig config;
	private final Set<WorldPoint> tiles = new HashSet<>();

	@Inject
    ForbiddenTileOverlay(Client client, LockedChunkOverlayConfig config)
	{
		this.client = client;
        this.config = config;
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
        // Map config darkness level to alpha
        int alpha;
        DarknessLevel lvl = config.darknessLevel();
        switch (lvl)
        {
            case MODERATE: alpha = 110; break;      // ~43%
            case DARK: alpha = 140; break;          // ~55%
            case VERY_DARK: alpha = 180; break;     // ~71% (previous default)
            case NEARLY_BLACK: alpha = 220; break;  // ~86%
            case PITCH_BLACK: alpha = 255; break;   // 100%
            default: alpha = 180;
        }
        final Color fill = new Color(0, 0, 0, alpha);

        // Group tiles by region to build one path per region
        Map<Integer, List<WorldPoint>> byRegion = new HashMap<>();
        for (WorldPoint wp : tiles)
        {
            if (wp.getPlane() != plane)
            {
                continue;
            }
            byRegion.computeIfAbsent(wp.getRegionID(), k -> new ArrayList<>()).add(wp);
        }

        for (List<WorldPoint> group : byRegion.values())
        {
            GeneralPath fillPath = new GeneralPath();
            Map<String, int[]> borderEdges = new HashMap<>();
            boolean any = false;
            for (WorldPoint wp : group)
            {
                LocalPoint lp = LocalPoint.fromWorld(client, wp);
                if (lp == null)
                {
                    continue;
                }
                Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                if (poly == null)
                {
                    continue;
                }
                // Append to region fill path
                fillPath.append(poly, false);
                any = true;

                // Count edges to find the outer border (edges seen once)
                for (int i = 0; i < poly.npoints; i++)
                {
                    int x1 = poly.xpoints[i];
                    int y1 = poly.ypoints[i];
                    int j = (i + 1) % poly.npoints;
                    int x2 = poly.xpoints[j];
                    int y2 = poly.ypoints[j];

                    // Normalize edge direction for key
                    String key;
                    if (x1 < x2 || (x1 == x2 && y1 <= y2))
                    {
                        key = x1 + "," + y1 + "|" + x2 + "," + y2;
                    }
                    else
                    {
                        key = x2 + "," + y2 + "|" + x1 + "," + y1;
                    }

                    int[] cnt = borderEdges.get(key);
                    if (cnt == null)
                    {
                        borderEdges.put(key, new int[]{1, x1, y1, x2, y2});
                    }
                    else
                    {
                        cnt[0]++;
                    }
                }
            }
            if (any)
            {
                if (((LockedChunkOverlayConfig)config).fillChunks())
                {
                    graphics.setColor(fill);
                    // Avoid a faint seam between adjacent forbidden regions by overwriting (no alpha blend)
                    Composite prevCompFill = graphics.getComposite();
                    Object prevAA = graphics.getRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING);
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
                    graphics.fill(fillPath);
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, prevAA);
                    graphics.setComposite(prevCompFill);
                }

                // Draw only edges that appear once (outer border), as simple line segments
                graphics.setColor(fill);
                BasicStroke prevStroke = (BasicStroke) graphics.getStroke();
                Composite prevComp = graphics.getComposite();
                graphics.setStroke(new BasicStroke(2f));
                // Avoid double-darkening by overwriting edge pixels instead of blending over the filled area
                graphics.setComposite(AlphaComposite.Src);
                for (int[] e : borderEdges.values())
                {
                    if (e[0] == 1)
                    {
                        graphics.drawLine(e[1], e[2], e[3], e[4]);
                    }
                }
                graphics.setStroke(prevStroke);
                graphics.setComposite(prevComp);
            }
        }

        graphics.setColor(prev);
        return null;
    }
}


