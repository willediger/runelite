/*
 * Copyright (c) 2025, Will Ediger <will.ediger@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.colosseumwaves;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.NPC;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Colosseum Waves",
	description = "Capture Fortis Colosseum wave spawns and pillar stacks and generate shareable links to line of sight tool for analysis",
	tags = {"fortis", "colosseum", "waves", "wave", "spawns", "los"},
	configName = "colosseumwaves"
)
@Slf4j
public class ColosseumWavesPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ColosseumWavesConfig config;

	@Inject
	private ManticoreHandler manticoreHandler;

	private ColosseumWavesPanel panel;
	private NavigationButton navButton;

	// Fortis Colosseum region ID
	private static final int COLOSSEUM_REGION_ID = 7216;

	// NPCs to exclude from tracking
	private static final Set<String> EXCLUDED_NPC_NAMES = ImmutableSet.of(
		"Minimus",
		"Sol Heredit",
		"Fremennik warband berserker",
		"Fremennik warband archer",
		"Fremennik warband seer"
	);

	// Colosseum wave NPCs with their LoS tool ID mapping
	private static final Map<Integer, Integer> COLOSSEUM_WAVE_NPCS =
		ImmutableMap.<Integer, Integer>builder()
			.put(NpcID.COLOSSEUM_STANDARD_MAGER, 1) // Serpent shaman
			.put(NpcID.COLOSSEUM_JAVELIN_COLOSSUS, 2) // Javelin Colossus
			.put(NpcID.COLOSSEUM_JAGUAR_WARRIOR, 3) // Jaguar warrior
			.put(NpcID.COLOSSEUM_MANTICORE, 4) // Manticore
			.put(NpcID.COLOSSEUM_MINOTAUR, 5) // Minotaur
			.put(NpcID.COLOSSEUM_MINOTAUR_ROUTEFIND, 5) // Minotaur (Red Flag)
			.put(NpcID.COLOSSEUM_SHOCKWAVE_COLOSSUS, 6) // Shockwave Colossus
			.build();

	// Pattern to match "Wave: X" messages
	private static final Pattern WAVE_PATTERN = Pattern.compile("Wave: (\\d+)");

	// Timing constants for wave spawn detection
	private static final int INITIAL_SPAWN_WINDOW_TICKS = 20;  // NPCs spawn within 20 ticks of wave start
	private static final int REINFORCEMENT_THRESHOLD_TICKS = 50; // After 50 ticks, spawns are reinforcements

	// LoS tool coordinate conversion
	private static final int LOS_COORD_OFFSET_X = 32;
	private static final int LOS_COORD_OFFSET_Y = 83;

	// State tracking
	private boolean inColosseum = false;
	private int currentWave = 0;
	private boolean waveComplete = false;
	private boolean isReinforcementWave = false;
	private boolean expectingWaveSpawn = false;
	private boolean hasProcessedReinforcements = false;

	// Timing
	private int waveStartTick = 0;

	// NPC spawn / reinforcements storage
	private final List<NPCSpawn> waveSpawns = new ArrayList<>();
	private final List<NPCSpawn> reinforcementSpawns = new ArrayList<>();

	// Player location at time of spawns (for URL generation)
	private Point playerLocationAtWaveSpawn = null;
	private Point playerLocationAtReinforcements = null;

	// Helper class to store NPC spawn info
	@Value
	private static class NPCSpawn
	{
		int npcId;
		Point location;
		NPC npcInstance;
	}

	@Provides
	ColosseumWavesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ColosseumWavesConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		resetState();

		panel = injector.getInstance(ColosseumWavesPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "colosseum_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Colosseum Waves")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (manticoreHandler != null)
		{
			manticoreHandler.clear();
		}

		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;

		resetState();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();
		Matcher matcher = WAVE_PATTERN.matcher(message);

		if (matcher.find())
		{
			int newWave = Integer.parseInt(matcher.group(1));

			// Reset panel ONLY when starting Wave 1 (new run)
			if (newWave == 1 && panel != null)
			{
				panel.reset();
			}

			currentWave = newWave;
			waveStartTick = client.getTickCount();
			expectingWaveSpawn = true;

			waveSpawns.clear();
			reinforcementSpawns.clear();
			playerLocationAtWaveSpawn = null;
			playerLocationAtReinforcements = null;
			waveComplete = false;
			isReinforcementWave = false;
			hasProcessedReinforcements = false;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (inColosseum && event.getGameState() == GameState.LOGGED_IN)
		{
			if (!isInColosseum())
			{
				resetState();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Check if we entered/left the Colosseum
		if (!inColosseum && isInColosseum())
		{
			inColosseum = true;
		}
		else if (inColosseum && !isInColosseum())
		{
			resetState();
		}

		// Handle wave spawns or reinforcements when ready
		if (inColosseum && waveComplete && !waveSpawns.isEmpty())
		{
			handleWaveSpawnsAndReinforcements();
			waveComplete = false;
		}
	}

	private Point getPlayerLoSLocation()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		WorldPoint worldLocation = localPlayer.getWorldLocation();
		LocalPoint localPoint = LocalPoint.fromWorld(client, worldLocation);

		if (localPoint != null)
		{
			Scene scene = client.getScene();
			Tile[][][] tiles = scene.getTiles();

			int sceneX = localPoint.getSceneX();
			int sceneY = localPoint.getSceneY();
			int plane = client.getPlane();

			if (sceneX >= 0 && sceneX < 104 && sceneY >= 0 && sceneY < 104 && plane >= 0 && plane < 4)
			{
				Tile playerTile = tiles[plane][sceneX][sceneY];
				if (playerTile != null)
				{
					Point sceneLocation = playerTile.getSceneLocation();
					return convertToLoSCoordinates(sceneLocation);
				}
			}
		}
		return null;
	}

	private boolean isInColosseum()
	{
		int[] mapRegions = client.getMapRegions();
		if (mapRegions != null)
		{
			for (int region : mapRegions)
			{
				if (region == COLOSSEUM_REGION_ID)
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean isManticore(NPC npc)
	{
		return npc != null && npc.getId() == NpcID.COLOSSEUM_MANTICORE;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (!inColosseum)
		{
			return;
		}

		NPC npc = event.getNpc();

		// Track manticores
		if (manticoreHandler != null && isManticore(npc))
		{
			manticoreHandler.onNpcSpawned(npc);
		}

		// Skip excluded NPCs
		if (npc.getName() != null && EXCLUDED_NPC_NAMES.contains(npc.getName()))
		{
			return;
		}

		// Only track Colosseum wave NPCs
		if (COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
		{
			Point npcLocation = getNPCSceneLocation(npc);
			if (npcLocation != null)
			{
				int currentTick = client.getTickCount();
				determineSpawnType(currentTick);

				NPCSpawn spawn = new NPCSpawn(npc.getId(), npcLocation, npc);

				if (!isReinforcementWave)
				{
					waveSpawns.add(spawn);
				}
				else
				{
					reinforcementSpawns.clear();
					reinforcementSpawns.addAll(collectActiveColosseumNPCs());
				}

				waveComplete = true;
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!inColosseum || manticoreHandler == null)
		{
			return;
		}

		// Only track manticore despawn for suffix tracking
		NPC npc = event.getNpc();
		if (isManticore(npc))
		{
			manticoreHandler.onNpcDespawned(npc);
		}
	}


	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		if (!inColosseum || manticoreHandler == null)
		{
			return;
		}

		if (event.getActor() instanceof NPC)
		{
			NPC npc = (NPC) event.getActor();
			if (isManticore(npc))
			{
				boolean wasUncharged = manticoreHandler.isManticoreUncharged(npc);
				manticoreHandler.checkNPCGraphics(npc);
				boolean isNowCharged = !manticoreHandler.isManticoreUncharged(npc);

				// If manticore just became charged, update the appropriate URL(s)
				if (wasUncharged && isNowCharged && currentWave > 0)
				{
					// Always update spawn URL
					updateSpawnUrlForCurrentWave();

					// If we've already processed reinforcements, also update reinforcement URL
					if (hasProcessedReinforcements)
					{
						updateReinforcementUrlForCurrentWave();
					}
				}
			}
		}
	}

	private List<NPCSpawn> collectActiveColosseumNPCs()
	{
		List<NPCSpawn> activeNPCs = new ArrayList<>();

		// Find all Colosseum NPCs currently in the scene
		for (NPC npc : client.getNpcs())
		{
			if (npc != null && COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
			{
				Point currentPos = getNPCSceneLocation(npc);
				if (currentPos != null)
				{
					activeNPCs.add(new NPCSpawn(npc.getId(), currentPos, npc));
				}
			}
		}

		return activeNPCs;
	}

	private Point getNPCSceneLocation(NPC npc)
	{
		WorldPoint worldPoint = npc.getWorldLocation();
		LocalPoint worldLocalPoint = LocalPoint.fromWorld(client, worldPoint);

		if (worldLocalPoint == null)
		{
			return null;
		}

		int sceneX = worldLocalPoint.getSceneX();
		int sceneY = worldLocalPoint.getSceneY();

		return new Point(sceneX, sceneY);
	}

	private Point convertToLoSCoordinates(Point sceneLocation)
	{
		int losX = sceneLocation.getX() - LOS_COORD_OFFSET_X;
		int losY = LOS_COORD_OFFSET_Y - sceneLocation.getY();

		return new Point(losX, losY);
	}

	private void handleWaveSpawnsAndReinforcements()
	{
		if (panel == null || currentWave <= 0)
		{
			return;
		}

		if (isReinforcementWave)
		{
			// Handle reinforcements
			if (!reinforcementSpawns.isEmpty())
			{
				// Capture player location at time of reinforcements
				if (config.includePlayerLocationReinforcements())
				{
					playerLocationAtReinforcements = getPlayerLoSLocation();
				}
				String url = buildLoSUrl(reinforcementSpawns, config.includePlayerLocationReinforcements(), playerLocationAtReinforcements);
				panel.setWaveReinforcementUrl(currentWave, url);
				hasProcessedReinforcements = true;
			}
		}
		else
		{
			// Handle initial spawns
			if (!waveSpawns.isEmpty())
			{
				// Capture player location at time of wave spawn
				if (config.includePlayerLocationSpawns())
				{
					playerLocationAtWaveSpawn = getPlayerLoSLocation();
				}
				String url = buildLoSUrl(waveSpawns, config.includePlayerLocationSpawns(), playerLocationAtWaveSpawn);
				panel.addWave(currentWave);
				panel.setWaveSpawnUrl(currentWave, url);
			}
		}

		// Reset reinforcement flag after processing
		if (isReinforcementWave)
		{
			isReinforcementWave = false;
		}
	}

	public String generateCurrentLoSLink()
	{
		if (!inColosseum)
		{
			return null;
		}

		// Capture current positions of all Colosseum NPCs on-demand
		List<NPCSpawn> currentSpawns = collectActiveColosseumNPCs();

		if (currentSpawns.isEmpty())
		{
			return null;
		}

		// For current LoS, get player's current position
		Point currentPlayerLocation = config.includePlayerLocationCurrent() ? getPlayerLoSLocation() : null;
		String url = buildLoSUrl(currentSpawns, config.includePlayerLocationCurrent(), currentPlayerLocation);
		return url;
	}

	private void determineSpawnType(int currentTick)
	{
		if (waveStartTick <= 0)
		{
			return;
		}

		int ticksSinceWaveStart = currentTick - waveStartTick;

		// First spawn within the initial window - this is a wave spawn
		if (expectingWaveSpawn && ticksSinceWaveStart < INITIAL_SPAWN_WINDOW_TICKS)
		{
			expectingWaveSpawn = false;
			isReinforcementWave = false;
		}
		// Spawns after the reinforcement threshold are reinforcements
		else if (ticksSinceWaveStart > REINFORCEMENT_THRESHOLD_TICKS && !isReinforcementWave)
		{
			isReinforcementWave = true;
		}
	}


	private void appendManticoreSuffixIfNeeded(StringBuilder urlBuilder, NPCSpawn spawn)
	{
		if (!isManticore(spawn.npcInstance))
		{
			return;
		}

		char suffix = 'u'; // Default to uncharged

		// Use the stored NPC instance to get its attack pattern
		if (spawn.npcInstance != null && manticoreHandler != null)
		{
			suffix = manticoreHandler.getManticoreLosSuffix(spawn.npcInstance);
		}

		urlBuilder.append(suffix);
	}

	private String buildLoSUrl(List<NPCSpawn> spawns, boolean includePlayer, Point playerLocation)
	{
		StringBuilder urlBuilder = new StringBuilder("https://los.colosim.com/?");

		for (NPCSpawn spawn : spawns)
		{
			Point losPos = convertToLoSCoordinates(spawn.location);
			Integer losNpcId = COLOSSEUM_WAVE_NPCS.get(spawn.npcId);

			if (losNpcId != null)
			{
				String spawnCode = String.format("%02d%02d%d", losPos.getX(), losPos.getY(), losNpcId);
				urlBuilder.append(spawnCode);

				appendManticoreSuffixIfNeeded(urlBuilder, spawn);
				urlBuilder.append(".");
			}
		}

		// Add player position if configured
		if (includePlayer && playerLocation != null)
		{
			int playerEncoded = playerLocation.getX() + (256 * playerLocation.getY());
			urlBuilder.append("#").append(playerEncoded);
		}

		return urlBuilder.toString();
	}

	private void updateSpawnUrlForCurrentWave()
	{
		if (panel != null && currentWave > 0 && !waveSpawns.isEmpty())
		{
			// Rebuild the spawn URL with updated manticore suffixes
			String url = buildLoSUrl(waveSpawns, config.includePlayerLocationSpawns(), playerLocationAtWaveSpawn);
			panel.setWaveSpawnUrl(currentWave, url);
		}
	}

	private void updateReinforcementUrlForCurrentWave()
	{
		if (panel != null && currentWave > 0 && !reinforcementSpawns.isEmpty())
		{
			// Rebuild the reinforcement URL with updated manticore suffixes
			String url = buildLoSUrl(reinforcementSpawns, config.includePlayerLocationReinforcements(), playerLocationAtReinforcements);
			panel.setWaveReinforcementUrl(currentWave, url);
		}
	}

	private void resetState()
	{
		waveSpawns.clear();
		reinforcementSpawns.clear();
		playerLocationAtWaveSpawn = null;
		playerLocationAtReinforcements = null;
		inColosseum = false;
		currentWave = 0;
		isReinforcementWave = false;
		expectingWaveSpawn = false;
		hasProcessedReinforcements = false;
		waveStartTick = 0;
	}
}