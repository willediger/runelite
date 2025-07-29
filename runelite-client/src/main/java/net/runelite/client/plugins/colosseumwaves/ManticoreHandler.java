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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.gameval.SpotanimID;

@Slf4j
@Singleton
public class ManticoreHandler
{
	@Inject
	private Client client;

	private static final int MAGIC_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_MAGIC_01;
	private static final int RANGED_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_RANGED_01;
	private static final int MELEE_ORB_GRAPHIC_ID = SpotanimID.VFX_MANTICORE_01_PROJECTILE_MELEE_01;

	private final Map<Integer, ManticoreData> manticores = new HashMap<>();
	private final Map<Integer, List<Integer>> orbSequences = new HashMap<>();

	private enum AttackType
	{
		MAGIC('m'), RANGED('r');
		final char losSuffix;

		AttackType(char suffix)
		{
			losSuffix = suffix;
		}
	}

	private static class ManticoreData
	{
		AttackType firstAttack = null;

		char getLosSuffix()
		{
			if (firstAttack == null)
			{
				return 'u'; // Uncharged
			}
			return firstAttack == AttackType.RANGED ? 'r' : 'm';
		}

		boolean isPatternKnown()
		{
			return firstAttack != null;
		}
	}

	public char getManticoreLosSuffix(NPC npc)
	{
		return getManticoreLosSuffix(npc.getIndex());
	}

	public char getManticoreLosSuffix(int npcIndex)
	{
		ManticoreData data = manticores.get(npcIndex);
		return data != null ? data.getLosSuffix() : 'u';
	}

	public boolean isManticoreUncharged(NPC npc)
	{
		return isManticoreUncharged(npc.getIndex());
	}

	public boolean isManticoreUncharged(int npcIndex)
	{
		ManticoreData data = manticores.get(npcIndex);
		return data == null || !data.isPatternKnown();
	}

	public void clear()
	{
		manticores.clear();
		orbSequences.clear();
	}

	public void onNpcSpawned(NPC npc)
	{
		int index = npc.getIndex();
		manticores.put(index, new ManticoreData());
		orbSequences.put(index, new ArrayList<>());
	}

	public void onNpcDespawned(NPC npc)
	{
		int index = npc.getIndex();
		manticores.remove(index);
		orbSequences.remove(index);
	}

	public void checkNPCGraphics(NPC npc)
	{
		int graphic = npc.getGraphic();
		if (graphic <= 0)
		{
			return;
		}

		if (graphic == MAGIC_ORB_GRAPHIC_ID || graphic == RANGED_ORB_GRAPHIC_ID || graphic == MELEE_ORB_GRAPHIC_ID)
		{
			int index = npc.getIndex();
			ManticoreData data = manticores.get(index);
			if (data == null)
			{
				return;
			}

			List<Integer> sequence = orbSequences.get(index);
			if (sequence == null)
			{
				return;
			}

			if (sequence.isEmpty() || sequence.get(sequence.size() - 1) != graphic)
			{
				sequence.add(graphic);

				if (sequence.size() == 1 && data.firstAttack == null)
				{
					if (graphic == MAGIC_ORB_GRAPHIC_ID)
					{
						data.firstAttack = AttackType.MAGIC;
					}
					else if (graphic == RANGED_ORB_GRAPHIC_ID)
					{
						data.firstAttack = AttackType.RANGED;
					}
				}

				if (sequence.size() >= 3)
				{
					sequence.clear();
				}
			}
		}
	}
}