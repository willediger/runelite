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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableMap;
import javax.inject.Inject;
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
//import net.runelite.client.plugins.colosseumwaves.NpcSpawn;

@PluginDescriptor(
	name = "Colosseum Waves",
	description = "Capture Fortis Colosseum wave spawns and pillar stacks and generate shareable links to colosim.com's line of sight tool for analysis",
	tags = {"fortis", "colosseum", "waves", "wave", "spawns", "los"},
	configName = "colosseumwaves"
)
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
	private static final Pattern WAVE_START_PATTERN = Pattern.compile("Wave: (\\d+)");

	// Pattern to match "Wave X complete!" messages
	private static final Pattern WAVE_COMPLETE_PATTERN = Pattern.compile("Wave (\\d+) completed");

	// LoS tool coordinate conversion
	private static final int LOS_COORD_OFFSET_X = 32;
	private static final int LOS_COORD_OFFSET_Y = 83;

	// State tracking
	private boolean inColosseum = false;
	private int currentWave = 0;

	private CaptureState captureState = CaptureState.NOT_STORED;
	private SpawnType spawnType = SpawnType.INITIAL;

	// Timing
	private int waveStartTick = 0;

	/**
	 * Tracker for the current wave's spawn information.
	 */
	private final CurrentWaveTracker currentWaveTracker = new CurrentWaveTracker();

	// Enum to represent spawn types
	private enum SpawnType
	{
		INITIAL,
		REINFORCEMENTS
	}

	// Enum to represent capture state
	private enum CaptureState
	{
		NOT_STORED,
		NPCS_STORED,
		LINK_GENERATED
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
		manticoreHandler.clear();

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
		Matcher waveStartMatcher = WAVE_START_PATTERN.matcher(message);
		Matcher waveCompleteMatcher = WAVE_COMPLETE_PATTERN.matcher(message);

		if (waveStartMatcher.find())
		{
			int newWave = Integer.parseInt(waveStartMatcher.group(1));

			// Reset panel ONLY when starting Wave 1 (new run)
			if (newWave == 1)
			{
				panel.reset();
			}

			currentWave = newWave;
			waveStartTick = client.getTickCount();
		}
		else if (waveCompleteMatcher.find())
		{
			clearCurrentWaveState();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{ // if the plugin thinks we're in the colosseum but we're not, reset state
		if (inColosseum && event.getGameState() == GameState.LOGGED_IN && !isInColosseum())
		{
			resetState();
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
			resetState();
			inColosseum = true;
		}
		else if (inColosseum && !isInColosseum())
		{
			resetState();
		}

		// If we're in a wave, update spawnType based on elapsed ticks
		if (waveStartTick > 0)
		{
			int currentTick = client.getTickCount();
			int ticksSinceWaveStart = currentTick - waveStartTick;
			if (ticksSinceWaveStart > 10 && spawnType == SpawnType.INITIAL)
			{
				spawnType = SpawnType.REINFORCEMENTS;
				captureState = CaptureState.NOT_STORED;
			}
		}

		// Handle wave spawns or reinforcements when ready
		if (inColosseum && captureState == CaptureState.NPCS_STORED)
		{
			handleWaveSpawnsAndReinforcements();
			captureState = CaptureState.LINK_GENERATED;
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
		if (isManticore(npc))
		{
			manticoreHandler.onNpcSpawned(npc);
		}

		// Only track Colosseum wave NPCs
		if (COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
		{
			Point npcLocation = getNPCSceneLocation(npc);
			// Only record spawns once per spawn type per wave
			if (npcLocation != null && captureState == CaptureState.NOT_STORED)
			{
				// Collect current active Colosseum NPCs and record them in the tracker
				List<NpcSpawn> spawns = collectActiveColosseumNPCs();
				if (spawnType == SpawnType.INITIAL)
				{
					currentWaveTracker.recordInitialSpawns(spawns);
				}
				else
				{
					currentWaveTracker.recordReinforcementSpawns(spawns);
				}

				captureState = CaptureState.NPCS_STORED;
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!inColosseum)
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
		if (!inColosseum)
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
				if (wasUncharged && isNowCharged)
				{
					// Always update spawn URL
					updateCurrentWaveUrl(SpawnType.INITIAL);

					// If we've already processed reinforcements, also update reinforcement URL
					if (spawnType == SpawnType.REINFORCEMENTS)
					{
						updateCurrentWaveUrl(SpawnType.REINFORCEMENTS);
					}
				}
			}
		}
	}

	private List<NpcSpawn> collectActiveColosseumNPCs()
	{
		List<NpcSpawn> activeNPCs = new ArrayList<>();

		// Find all Colosseum NPCs currently in the scene
		for (NPC npc : client.getNpcs())
		{
			if (npc != null && COLOSSEUM_WAVE_NPCS.containsKey(npc.getId()))
			{
				Point currentPos = getNPCSceneLocation(npc);
				if (currentPos != null)
				{
					// Store the NPC ID, scene location and NPC index in the spawn record
					activeNPCs.add(new NpcSpawn(npc.getId(), currentPos, npc.getIndex()));
				}
			}
		}

		return activeNPCs;
	}

	private Point getNPCSceneLocation(NPC npc)
	{
		WorldPoint worldPoint = npc.getWorldLocation();
		LocalPoint worldLocalPoint = LocalPoint.fromWorld(client, worldPoint);

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
		switch (spawnType)
		{
			case INITIAL:
				// Handle initial spawns for the current wave
				if (currentWaveTracker.hasWaveSpawns())
				{
					// Record the player's location at spawn time if configured
					if (config.includePlayerLocationSpawns())
					{
						currentWaveTracker.setPlayerLocationAtWaveSpawn(getPlayerLoSLocation());
					}
					// Add a new row for this wave before updating the URL
					panel.addWave(currentWave);
					updateCurrentWaveUrl(SpawnType.INITIAL);
				}
				break;

			case REINFORCEMENTS:
				// Handle reinforcement spawns for the current wave
				if (currentWaveTracker.hasReinforcements())
				{
					if (config.includePlayerLocationReinforcements())
					{
						currentWaveTracker.setPlayerLocationAtReinforcements(getPlayerLoSLocation());
					}
					updateCurrentWaveUrl(SpawnType.REINFORCEMENTS);
				}
				break;
		}
	}

	public String generateCurrentLoSLink()
	{
		if (!inColosseum)
		{
			return null;
		}

		// Capture current positions of all Colosseum NPCs on-demand
		List<NpcSpawn> currentSpawns = collectActiveColosseumNPCs();

		if (currentSpawns.isEmpty())
		{
			return null;
		}

		// For current LoS, get player's current position if configured
		Point currentPlayerLocation = config.includePlayerLocationCurrent() ? getPlayerLoSLocation() : null;
		return buildLoSUrl(currentSpawns, config.includePlayerLocationCurrent(), currentPlayerLocation);
	}

	private void appendManticoreSuffixIfNeeded(StringBuilder urlBuilder, NpcSpawn spawn)
	{
		// Only append a suffix for manticores
		if (spawn.getNpcId() != NpcID.COLOSSEUM_MANTICORE)
		{
			return;
		}

		// Look up the attack pattern for this manticore by its NPC index
		char suffix = manticoreHandler.getManticoreLosSuffix(spawn.getNpcIndex());
		urlBuilder.append(suffix);
	}

	private String buildLoSUrl(List<NpcSpawn> spawns, boolean includePlayer, Point playerLocation)
	{
		StringBuilder urlBuilder = new StringBuilder("https://los.colosim.com/?");

		for (NpcSpawn spawn : spawns)
		{
			Point losPos = convertToLoSCoordinates(spawn.getLocation());
			Integer losNpcId = COLOSSEUM_WAVE_NPCS.get(spawn.getNpcId());

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

	/**
	 * Update the panel with a new LoS URL for the current wave.  This method
	 * centralises URL generation for both initial spawns and reinforcements.
	 * It rebuilds the URL based on the current spawn list, manticore attack
	 * patterns and optional player position.
	 *
	 * @param type whether to update the initial spawn URL or reinforcement URL
	 */
	private void updateCurrentWaveUrl(SpawnType type)
	{
		if (panel == null || currentWave <= 0)
		{
			return;
		}

		List<NpcSpawn> spawns;
		boolean includePlayer;
		Point playerLocation;

		if (type == SpawnType.INITIAL)
		{
			spawns = currentWaveTracker.getWaveSpawns();
			if (spawns.isEmpty())
			{
				return;
			}
			includePlayer = config.includePlayerLocationSpawns();
			playerLocation = currentWaveTracker.getPlayerLocationAtWaveSpawn();

			String url = buildLoSUrl(spawns, includePlayer, playerLocation);
			panel.setWaveSpawnUrl(currentWave, url);
		}
		else
		{
			spawns = currentWaveTracker.getReinforcementSpawns();
			if (spawns.isEmpty())
			{
				return;
			}
			includePlayer = config.includePlayerLocationReinforcements();
			playerLocation = currentWaveTracker.getPlayerLocationAtReinforcements();

			String url = buildLoSUrl(spawns, includePlayer, playerLocation);
			panel.setWaveReinforcementUrl(currentWave, url);
		}
	}

	private void resetState()
	{
		inColosseum = false;
		clearCurrentWaveState();
	}

	private void clearCurrentWaveState()
	{
		// Reset state for the next wave
		currentWave = 0;
		waveStartTick = 0;
		captureState = CaptureState.NOT_STORED;
		spawnType = SpawnType.INITIAL;

		// Clear all recorded spawns and player positions using the tracker
		currentWaveTracker.reset();
	}
}