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

private boolean reportIfForbidden(WorldPoint wp, String dir, boolean hasList, List<String> unlocked)
{
	int rid = wp.getRegionID();
	boolean permitted = !hasList || unlocked.contains(Integer.toString(rid));
	if (!permitted)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", dir + ": (" + wp.getX() + "," + wp.getY() + "," + wp.getPlane() + ") regionId=" + rid + " permitted=false", null);
		return true;
	}
	return false;
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

		// On movement, print only forbidden tiles with direction; and shade them
		if (lastTile == null || !lastTile.equals(worldPoint))
		{
			Set<WorldPoint> forbidden = new HashSet<>();
			if (reportIfForbidden(worldPoint, "HERE", hasList, unlocked)) forbidden.add(worldPoint);
			int px = worldPoint.getX();
			int py = worldPoint.getY();
			int pl = worldPoint.getPlane();
			WorldPoint n = new WorldPoint(px, py + 5, pl);
			WorldPoint s = new WorldPoint(px, py - 5, pl);
			WorldPoint e = new WorldPoint(px + 5, py, pl);
			WorldPoint w = new WorldPoint(px - 5, py, pl);
			if (reportIfForbidden(n, "NORTH", hasList, unlocked)) forbidden.add(n);
			if (reportIfForbidden(s, "SOUTH", hasList, unlocked)) forbidden.add(s);
			if (reportIfForbidden(e, "EAST", hasList, unlocked)) forbidden.add(e);
			if (reportIfForbidden(w, "WEST", hasList, unlocked)) forbidden.add(w);
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
