package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SoundEffectVolume;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Example"
)
public class ExamplePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ExampleConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlackoutOverlay blackoutOverlay;

	@Inject
	private ForbiddenTileOverlay forbiddenTileOverlay;

	private Integer lastRegionId;

	private boolean inLockedRegion;
	private long lockedEnterMs;
	private int lastRemainingSec = -1;
	private long lastGrueMs;
	private WorldPoint lastTile;
	private static final int RECT_WIDTH = 201; // tiles, perpendicular to direction
	private static final int RECT_LENGTH = 101; // tiles, along the direction
	private static final int WARNING_DISTANCE = 20; // tiles, warning distance from forbidden chunk border

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
		overlayManager.add(forbiddenTileOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
		lastTile = null;
		overlayManager.remove(forbiddenTileOverlay);
	}

private void printForbidden(String dir, WorldPoint wp)
{
	int rid = wp.getRegionID();
    int baseX = (rid >> 8) << 6; // southwest corner x of 64x64 region
    int baseY = (rid & 255) << 6; // southwest corner y
    int maxX = baseX + 63;
    int maxY = baseY + 63;
    client.addChatMessage(
        ChatMessageType.GAMEMESSAGE,
        "",
        dir + ": (" + wp.getX() + "," + wp.getY() + "," + wp.getPlane() + ") regionId=" + rid +
            " permitted=false border=(" + baseX + "," + baseY + ")-(" + maxX + "," + maxY + ")",
        null
    );
}

private void addLocalInstance(Set<WorldPoint> out, WorldPoint wp)
{
	var locals = WorldPoint.toLocalInstance(client, wp);
	if (locals == null || locals.isEmpty())
	{
		out.add(wp);
		return;
	}
	out.addAll(locals);
}

private WorldPoint nearestForbiddenWithin5(int px, int py, int pl, int dx, int dy, boolean hasList, List<String> unlocked)
{
	for (int d = 0; d <= WARNING_DISTANCE; d++)
	{
		WorldPoint t = new WorldPoint(px + dx * d, py + dy * d, pl);
		// Tiles outside mainland are always permitted
		if (!isWithinMainland(t))
		{
			continue;
		}
		int rid = t.getRegionID();
		boolean permitted = !hasList || unlocked.contains(Integer.toString(rid));
		if (!permitted)
		{
			return t;
		}
	}
	return null;
}

private boolean isWithinMainland(WorldPoint wp)
{
    int x = wp.getX();
    int y = wp.getY();
    return x >= 1024 && x <= 3967 && y >= 2496 && y <= 4159;
}

private void addRectTiles(Set<WorldPoint> out, WorldPoint start, int dx, int dy, int length, int width)
{
	int halfW = Math.max(0, (width - 1) / 2);
	for (int i = 0; i < length; i++)
	{
		int baseX = start.getX() + dx * i;
		int baseY = start.getY() + dy * i;
		// Perpendicular vector
		int px = -dy;
		int py = dx;
		for (int j = -halfW; j <= halfW; j++)
		{
			WorldPoint p = new WorldPoint(baseX + px * j, baseY + py * j, start.getPlane());
			// Respect mainland rules: skip tiles outside mainland
			if (!isWithinMainland(p))
			{
				continue;
			}
			addLocalInstance(out, p);
		}
	}
}

private void addRectTilesOneSided(Set<WorldPoint> out, WorldPoint start, int dx, int dy, int length, int width, int sideX, int sideY)
{
    for (int i = 0; i < length; i++)
    {
        int baseX = start.getX() + dx * i;
        int baseY = start.getY() + dy * i;
        for (int j = 0; j < width; j++)
        {
            WorldPoint p = new WorldPoint(baseX + sideX * j, baseY + sideY * j, start.getPlane());
            if (!isWithinMainland(p))
            {
                continue;
            }
            addLocalInstance(out, p);
        }
    }
}

private void addRegionTiles(Set<WorldPoint> out, int regionId, int plane)
{
    int baseX = (regionId >> 8) << 6;
    int baseY = (regionId & 255) << 6;
    for (int rx = 0; rx < 64; rx++)
    {
        for (int ry = 0; ry < 64; ry++)
        {
            WorldPoint p = new WorldPoint(baseX + rx, baseY + ry, plane);
            if (!isWithinMainland(p))
            {
                continue;
            }
            addLocalInstance(out, p);
        }
    }
}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		int[] currentRegions = client.getMapRegions();
		if (currentRegions == null)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint worldPoint = localPlayer != null ? localPlayer.getWorldLocation() : null;
		if (worldPoint == null)
		{
			return;
		}

		int regionId = worldPoint.getRegionID();
		String csv = configManager.getConfiguration("regionlocker", "unlockedRegions");
		List<String> unlocked = (csv == null || csv.isEmpty()) ? Collections.emptyList() : Text.fromCSV(csv);
        boolean hasList = unlocked != null && !unlocked.isEmpty();
        boolean isUnlocked = !hasList || unlocked.contains(Integer.toString(regionId));
        if (!isWithinMainland(worldPoint))
        {
            // Outside mainland bounds is always permitted
            isUnlocked = true;
        }

		// On movement, print only forbidden tiles with direction; and shade the nearest forbidden per ray
		if (lastTile == null || !lastTile.equals(worldPoint))
		{
			Set<WorldPoint> forbidden = new HashSet<>();
			int px = worldPoint.getX();
			int py = worldPoint.getY();
			int pl = worldPoint.getPlane();

			WorldPoint here = nearestForbiddenWithin5(px, py, pl, 0, 0, hasList, unlocked);
			WorldPoint north = nearestForbiddenWithin5(px, py, pl, 0, +1, hasList, unlocked);
			WorldPoint south = nearestForbiddenWithin5(px, py, pl, 0, -1, hasList, unlocked);
			WorldPoint east = nearestForbiddenWithin5(px, py, pl, +1, 0, hasList, unlocked);
			WorldPoint west = nearestForbiddenWithin5(px, py, pl, -1, 0, hasList, unlocked);
			WorldPoint northeast = nearestForbiddenWithin5(px, py, pl, +1, +1, hasList, unlocked);
			WorldPoint northwest = nearestForbiddenWithin5(px, py, pl, -1, +1, hasList, unlocked);
			WorldPoint southeast = nearestForbiddenWithin5(px, py, pl, +1, -1, hasList, unlocked);
			WorldPoint southwest = nearestForbiddenWithin5(px, py, pl, -1, -1, hasList, unlocked);

			if (here != null) { printForbidden("HERE", here); addRegionTiles(forbidden, here.getRegionID(), here.getPlane()); }
            if (north != null)
			{
				printForbidden("NORTH", north);
                addRegionTiles(forbidden, north.getRegionID(), north.getPlane());
			}
            if (south != null)
			{
				printForbidden("SOUTH", south);
                addRegionTiles(forbidden, south.getRegionID(), south.getPlane());
			}
            if (east != null)
			{
				printForbidden("EAST", east);
                addRegionTiles(forbidden, east.getRegionID(), east.getPlane());
			}
            if (west != null)
			{
				printForbidden("WEST", west);
                addRegionTiles(forbidden, west.getRegionID(), west.getPlane());
			}

			if (northeast != null)
			{
				printForbidden("NORTHEAST", northeast);
				addRegionTiles(forbidden, northeast.getRegionID(), northeast.getPlane());
			}
			if (northwest != null)
			{
				printForbidden("NORTHWEST", northwest);
				addRegionTiles(forbidden, northwest.getRegionID(), northwest.getPlane());
			}
			if (southeast != null)
			{
				printForbidden("SOUTHEAST", southeast);
				addRegionTiles(forbidden, southeast.getRegionID(), southeast.getPlane());
			}
			if (southwest != null)
			{
				printForbidden("SOUTHWEST", southwest);
				addRegionTiles(forbidden, southwest.getRegionID(), southwest.getPlane());
			}

			forbiddenTileOverlay.setTiles(forbidden);
			lastTile = worldPoint;
		}

		if (lastRegionId == null || !lastRegionId.equals(regionId))
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "regionId=" + regionId + " unlocked=" + isUnlocked, null);
			lastRegionId = regionId;
		}

		long now = System.currentTimeMillis();
		if (!isUnlocked)
		{
			if (!inLockedRegion)
			{
				inLockedRegion = true;
				lockedEnterMs = now;
				lastRemainingSec = -1;
				lastGrueMs = 0L;
			}

			long elapsedSec = Math.max(0L, (now - lockedEnterMs) / 1000L);
			int remainingSec = (int) Math.max(0L, 5L - elapsedSec);
			// Update blackout fade level each tick: 0s -> 255 alpha, 5s -> ~51 alpha
			int alpha = (int)Math.max(0, Math.min(255, (5 - remainingSec) * (255 / 5.0)));
			if (lastRemainingSec == -1 && remainingSec > 0)
			{
				// First entry into locked region: add overlay and start faded
				overlayManager.add(blackoutOverlay);
			}
			blackoutOverlay.setState(alpha, false);
			if (remainingSec != lastRemainingSec)
			{
				int volume;
				switch (remainingSec)
				{
					case 5:
						volume = SoundEffectVolume.LOW;
						break;
					case 4:
						volume = SoundEffectVolume.MEDIUM_LOW;
						break;
					case 3:
						volume = SoundEffectVolume.MEDIUM_HIGH;
						break;
					default: // 2, 1, 0
						volume = SoundEffectVolume.HIGH;
				}
				if (remainingSec > 0)
				{
					client.playSoundEffect(3482, volume);
				}
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You arent supposed to be here, you will perish in " + remainingSec + " seconds.", null);
				// Crossed a boundary; update last seen
				lastRemainingSec = remainingSec;
			}

			// While at 0 or below, continuously show opaque blackout and repeat grue warning each second
			if (remainingSec <= 0)
			{
				overlayManager.add(blackoutOverlay);
				blackoutOverlay.setState(255, true);
				if (now - lastGrueMs >= 200L)
				{
					client.playSoundEffect(3485, SoundEffectVolume.HIGH);
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "You have been eaten by a grue.", null);
					lastGrueMs = now;
				}
			}
		}
		else if (inLockedRegion)
		{
			long durationSec = Math.max(0L, (now - lockedEnterMs) / 1000L);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Time in locked region: " + durationSec + "s", null);
			inLockedRegion = false;
			lastRemainingSec = -1;
			overlayManager.remove(blackoutOverlay);
			lastGrueMs = 0L;
		}
	}

	@Provides
	ExampleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExampleConfig.class);
	}
}
