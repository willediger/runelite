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

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.Box;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import net.runelite.client.util.LinkBrowser;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColosseumWavesPanel extends PluginPanel
{
	private static final int componentHeight = 30;
	private static final int gapSize = 5;
	private static final int waveNumberWidth = 42;
	private static final int spawnButtonWidth = 62;
	private static final int reinforcementsButtonWidth = 118;
	private static final Color bgColor = ColorScheme.DARK_GRAY_COLOR;
	private static final Color buttonColor = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color hoverColor = new Color(52, 52, 52);

	private final ColosseumWavesPlugin plugin;

	private final JPanel wavesContainer;
	private final List<WavePanel> wavePanels = new ArrayList<>();

	@Inject
	public ColosseumWavesPanel(final ColosseumWavesPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setBackground(bgColor);
		setLayout(new BorderLayout());

		// Header - "Current LoS" button + static "Waves" header
		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBorder(new EmptyBorder(gapSize, gapSize, gapSize, gapSize));

		JButton currentLoS = buildHeaderButton("Current LoS", buttonColor, hoverColor);
		currentLoS.addActionListener(e ->
		{
			String url = plugin.generateCurrentLoSLink();
			if (url != null)
			{
				LinkBrowser.browse(url);
			}
		});

		JLabel wavesLabel = new JLabel("Waves", SwingConstants.CENTER);
		wavesLabel.setOpaque(true);
		wavesLabel.setBackground(buttonColor);
		wavesLabel.setForeground(Color.WHITE);
		wavesLabel.setPreferredSize(new Dimension(Integer.MAX_VALUE, componentHeight));
		wavesLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, componentHeight));

		header.add(currentLoS);
		header.add(Box.createRigidArea(new Dimension(0, gapSize)));
		header.add(wavesLabel);

		add(header, BorderLayout.NORTH);

		wavesContainer = new JPanel();
		wavesContainer.setLayout(new BoxLayout(wavesContainer, BoxLayout.Y_AXIS));
		wavesContainer.setBackground(bgColor);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(bgColor);
		wrapper.add(wavesContainer, BorderLayout.NORTH);

		add(wrapper, BorderLayout.CENTER);
	}

	public void addWave(int waveNumber)
	{
		SwingUtilities.invokeLater(() ->
		{
			WavePanel panel = new WavePanel(waveNumber);
			wavePanels.add(panel);
			wavesContainer.add(panel);
			wavesContainer.add(Box.createRigidArea(new Dimension(0, gapSize)));
			wavesContainer.revalidate();
		});
	}

	public void setWaveSpawnUrl(int waveNumber, String url)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (waveNumber > 0 && waveNumber <= wavePanels.size())
			{
				wavePanels.get(waveNumber - 1).setSpawnUrl(url);
			}
		});
	}

	public void setWaveReinforcementUrl(int waveNumber, String url)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (waveNumber > 0 && waveNumber <= wavePanels.size())
			{
				wavePanels.get(waveNumber - 1).setReinforcementUrl(url);
			}
		});
	}

	public void reset()
	{
		SwingUtilities.invokeLater(() ->
		{
			wavePanels.clear();
			wavesContainer.removeAll();
			wavesContainer.revalidate();
		});
	}

	private static JButton buildHeaderButton(String text, Color bg, Color hover)
	{
		return createStyledButton(text, bg, hover, new Dimension(Integer.MAX_VALUE, componentHeight));
	}

	private static class WavePanel extends JPanel
	{
		private final JLabel numberLabel;
		private final JButton spawnButton;
		private final JButton reinfButton;

		WavePanel(int wave)
		{
			setOpaque(false);
			setLayout(new BorderLayout());

			setBorder(new EmptyBorder(0, gapSize, 0, gapSize));

			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
			row.setOpaque(false);
			row.setPreferredSize(new Dimension(Integer.MAX_VALUE, componentHeight));
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, componentHeight));

			// Wave number label
			numberLabel = buildCellLabel(String.valueOf(wave));
			setFixedSize(numberLabel, waveNumberWidth, componentHeight);
			row.add(numberLabel);
			row.add(Box.createRigidArea(new Dimension(gapSize, 0)));

			// Spawn button
			spawnButton = buildCellButton("Spawn");
			setFixedSize(spawnButton, spawnButtonWidth, componentHeight);
			spawnButton.setEnabled(false);
			row.add(spawnButton);
			row.add(Box.createRigidArea(new Dimension(gapSize, 0)));

			// Reinforcements button
			reinfButton = buildCellButton("Reinforcements");
			setFixedSize(reinfButton, reinforcementsButtonWidth, componentHeight);
			reinfButton.setHorizontalAlignment(SwingConstants.CENTER);
			reinfButton.setEnabled(false);
			reinfButton.setVisible(false);
			row.add(reinfButton);

			add(row, BorderLayout.CENTER);
		}


		void setSpawnUrl(String url)
		{
			enableButton(spawnButton, url);
		}

		void setReinforcementUrl(String url)
		{
			reinfButton.setVisible(true);
			enableButton(reinfButton, url);
		}

		private static JLabel buildCellLabel(String txt)
		{
			JLabel l = new JLabel(txt, SwingConstants.CENTER);
			l.setOpaque(true);
			l.setBackground(buttonColor);
			l.setForeground(Color.WHITE);
			l.setPreferredSize(new Dimension(Integer.MAX_VALUE, componentHeight));
			l.setMaximumSize(new Dimension(Integer.MAX_VALUE, componentHeight));
			return l;
		}

		private static JButton buildCellButton(String txt)
		{
			return createStyledButton(txt, buttonColor, hoverColor, new Dimension(Integer.MAX_VALUE, componentHeight));
		}

		private static void enableButton(JButton b, String url)
		{
			for (var l : b.getActionListeners())
			{
				b.removeActionListener(l);
			}
			b.addActionListener(e -> LinkBrowser.browse(url));
			b.setEnabled(true);
			b.setForeground(Color.WHITE);
		}
	}

	/**
	 * Creates a styled button with hover effects.
	 * Consolidates common button creation logic to avoid duplication.
	 */
	private static JButton createStyledButton(String text, Color bgColor, Color hoverColor, Dimension size)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setBackground(bgColor);
		button.setForeground(Color.WHITE);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setFixedSize(button, size.width, size.height);
		button.setBorder(BorderFactory.createEmptyBorder());

		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(hoverColor);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(bgColor);
			}
		});

		return button;
	}

	/**
	 * Sets a fixed size for a component (preferred, maximum, and minimum).
	 * Consolidates repetitive size setting code.
	 */
	private static void setFixedSize(JComponent component, int width, int height)
	{
		Dimension size = new Dimension(width, height);
		component.setPreferredSize(size);
		component.setMaximumSize(size);
		component.setMinimumSize(size);
	}

}