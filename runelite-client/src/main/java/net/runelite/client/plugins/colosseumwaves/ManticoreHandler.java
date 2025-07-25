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

import net.runelite.api.Client;
import net.runelite.api.NPC;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class ManticoreHandler
{
	private final Client client;
	private final ColosseumWavesPlugin plugin;

	private static final int MANTICORE_NPC_ID = 12818;

	private static final int MAGIC_ORB = 2681;
	private static final int RANGED_ORB = 2683;
	private static final int MELEE_ORB = 2685;

	public ManticoreHandler(Client client, ColosseumWavesPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	private final Map<NPC, MData> manticores = new HashMap<>();

	private final Map<NPC, List<Integer>> orbSequences = new HashMap<>();

	private enum AType
	{
		MAGIC('m'), RANGED('r');
		final char suf;

		AType(char c)
		{
			suf = c;
		}
	}

	private static class MData
	{
		AType first = null;

		char suf()
		{
			return first == AType.RANGED ? 'r' : 'm';
		}

		boolean known()
		{
			return first != null;
		}
	}

	public char getManticoreLosSuffix(NPC n)
	{
		MData d = manticores.get(n);
		return d != null ? d.suf() : 'm';
	}

	public void clear()
	{
		manticores.clear();
		orbSequences.clear();
	}

	public void onNpcSpawned(NPC n)
	{
		if (n.getId() == MANTICORE_NPC_ID)
		{
			manticores.put(n, new MData());
			orbSequences.put(n, new ArrayList<>());
		}
	}

	public void onNpcDespawned(NPC n)
	{
		manticores.remove(n);
		orbSequences.remove(n);
	}

	public void checkNPCGraphics(NPC npc)
	{
		if (npc == null || npc.getId() != MANTICORE_NPC_ID)
		{
			return;
		}

		int graphic = npc.getGraphic();
		if (graphic <= 0)
		{
			return;
		}

		if (graphic == MAGIC_ORB || graphic == RANGED_ORB || graphic == MELEE_ORB)
		{
			MData data = manticores.get(npc);
			if (data == null)
			{
				return;
			}

			List<Integer> sequence = orbSequences.get(npc);
			if (sequence == null)
			{
				return;
			}

			if (sequence.isEmpty() || sequence.get(sequence.size() - 1) != graphic)
			{
				sequence.add(graphic);

				if (sequence.size() == 1 && data.first == null)
				{
					if (graphic == MAGIC_ORB)
					{
						data.first = AType.MAGIC;
					}
					else if (graphic == RANGED_ORB)
					{
						data.first = AType.RANGED;
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