package net.runelite.client.plugins.colosseumwaves;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColosseumWavesPanel extends PluginPanel
{
    private final ColosseumWavesPlugin plugin;
    private final JPanel wavesContainer;
    private final List<WavePanel> wavePanels = new ArrayList<>();

    @Inject
    public ColosseumWavesPanel(ColosseumWavesPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        final Color bgColor = ColorScheme.DARK_GRAY_COLOR;
        final Color hoverColor = ColorScheme.DARKER_GRAY_HOVER_COLOR;
        final Color buttonColor = ColorScheme.DARKER_GRAY_COLOR;

        setBackground(bgColor);
        setLayout(new BorderLayout());

        // Create header panel with "Current LoS" button and "Waves" label
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(bgColor);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Current LoS button
        JButton currentLoSButton = new JButton("Current LoS");
        currentLoSButton.setFocusPainted(false);
        currentLoSButton.setBackground(buttonColor);
        currentLoSButton.setForeground(Color.WHITE);
        currentLoSButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        currentLoSButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        currentLoSButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        currentLoSButton.setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));
        currentLoSButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        currentLoSButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                currentLoSButton.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                currentLoSButton.setBackground(buttonColor);
            }
        });

        currentLoSButton.addActionListener(e -> {
            // Get the current LoS link from the plugin
            String currentUrl = plugin.generateCurrentLoSLink();
            if (currentUrl != null) {
                openWebpage(currentUrl);
            }
        });

        // Waves label (styled as a button but non-functional)
        JLabel wavesLabel = new JLabel("Waves");
        wavesLabel.setOpaque(true);
        wavesLabel.setBackground(buttonColor);
        wavesLabel.setForeground(Color.WHITE);
        wavesLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        wavesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        wavesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        wavesLabel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));
        wavesLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        headerPanel.add(currentLoSButton);
        headerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        headerPanel.add(wavesLabel);

        add(headerPanel, BorderLayout.NORTH);

        // Create scrollable container for waves
        wavesContainer = new JPanel();
        wavesContainer.setLayout(new BoxLayout(wavesContainer, BoxLayout.Y_AXIS));
        wavesContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel scrollWrapper = new JPanel();
        scrollWrapper.setLayout(new BorderLayout());
        scrollWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollWrapper.add(wavesContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollWrapper);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void openWebpage(String url)
    {
        try
        {
            Desktop.getDesktop().browse(new URI(url));
        }
        catch (Exception ex)
        {
            log.debug("[DEBUG] Error opening URL: {}", ex);
        }
    }

    public void addWave(int waveNumber)
    {
        log.debug("[DEBUG ColosseumWavesPanel] addWave called for wave {}", waveNumber);
        SwingUtilities.invokeLater(() -> {
            log.debug("[DEBUG ColosseumWavesPanel] addWave executing in Swing thread for wave {}", waveNumber);
            WavePanel wavePanel = new WavePanel(waveNumber);
            wavePanels.add(wavePanel);
            wavesContainer.add(wavePanel);
            wavesContainer.add(Box.createRigidArea(new Dimension(0, 5)));

            wavesContainer.revalidate();
            this.revalidate();

            log.debug("[DEBUG ColosseumWavesPanel] Wave {} panel added successfully", waveNumber);
            log.debug("[DEBUG ColosseumWavesPanel] Container has {} components", wavesContainer.getComponentCount());
            log.debug("[DEBUG ColosseumWavesPanel] Panel visible: {}", wavePanel.isVisible());
        });
    }

    public void setWaveSpawnUrl(int waveNumber, String url)
    {
        log.debug("[DEBUG ColosseumWavesPanel] setWaveSpawnUrl called for wave {} with URL: {}", waveNumber, url);
        SwingUtilities.invokeLater(() -> {
            log.debug("[DEBUG ColosseumWavesPanel] setWaveSpawnUrl executing in Swing thread for wave {}", waveNumber);
            if (waveNumber > 0 && waveNumber <= wavePanels.size())
            {
                wavePanels.get(waveNumber - 1).setSpawnUrl(url);
                log.debug("[DEBUG ColosseumWavesPanel] Wave {} spawn URL set successfully", waveNumber);
            }
            else
            {
                log.debug("[DEBUG ColosseumWavesPanel] ERROR: Wave {} not found! wavePanels.size() = {}", waveNumber, wavePanels.size());
            }
        });
    }

    public void setWaveReinforcementUrl(int waveNumber, String url)
    {
        log.debug("[DEBUG ColosseumWavesPanel] setWaveReinforcementUrl called for wave {} with URL: {}", waveNumber, url);
        SwingUtilities.invokeLater(() -> {
            log.debug("[DEBUG ColosseumWavesPanel] setWaveReinforcementUrl executing in Swing thread for wave {}", waveNumber);
            if (waveNumber > 0 && waveNumber <= wavePanels.size())
            {
                wavePanels.get(waveNumber - 1).setReinforcementUrl(url);
                log.debug("[DEBUG ColosseumWavesPanel] Wave {} reinforcement URL set successfully", waveNumber);
            }
            else
            {
                log.debug("[DEBUG ColosseumWavesPanel] ERROR: Wave {} not found! wavePanels.size() = {}", waveNumber, wavePanels.size());
            }
        });
    }

    public void reset()
    {
        SwingUtilities.invokeLater(() -> {
            wavePanels.clear();
            wavesContainer.removeAll();
            wavesContainer.revalidate();
        });
    }

    private static class WavePanel extends JPanel
    {
        private final JLabel waveLabel;
        private final JButton spawnButton;
        private JButton reinforcementButton;
        private final JPanel buttonsPanel;
        private final JPanel placeholderPanel;
        private String spawnUrl;
        private String reinforcementUrl;

        public WavePanel(int waveNumber)
        {
            setLayout(new BorderLayout());
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));

            // Wave label
            waveLabel = new JLabel("Wave " + waveNumber);
            waveLabel.setForeground(Color.WHITE);
            waveLabel.setFont(waveLabel.getFont().deriveFont(Font.BOLD));
            waveLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
            add(waveLabel, BorderLayout.NORTH);

            // Buttons panel - always 2 columns
            buttonsPanel = new JPanel();
            buttonsPanel.setLayout(new GridLayout(1, 2, 5, 0));
            buttonsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

            spawnButton = createLinkButton("Spawn");
            buttonsPanel.add(spawnButton);

            // Add empty placeholder panel for the second column
            placeholderPanel = new JPanel();
            placeholderPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            buttonsPanel.add(placeholderPanel);

            add(buttonsPanel, BorderLayout.CENTER);
        }

        private JButton createLinkButton(String text)
        {
            JButton button = new JButton(text);
            button.setFocusPainted(false);
            button.setBackground(ColorScheme.DARK_GRAY_COLOR);
            button.setForeground(Color.GRAY);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                    BorderFactory.createEmptyBorder(3, 5, 3, 5)
            ));
            button.setEnabled(false);
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));

            button.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    if (button.isEnabled())
                    {
                        button.setBackground(ColorScheme.DARK_GRAY_COLOR.brighter());
                    }
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    button.setBackground(ColorScheme.DARK_GRAY_COLOR);
                }
            });

            return button;
        }

        public void setSpawnUrl(String url)
        {
            this.spawnUrl = url;
            spawnButton.setEnabled(true);
            spawnButton.setForeground(Color.WHITE);

            // Remove old listeners and add new one
            for (var listener : spawnButton.getActionListeners())
            {
                spawnButton.removeActionListener(listener);
            }

            spawnButton.addActionListener(e -> openUrl(url));
        }

        public void setReinforcementUrl(String url)
        {
            this.reinforcementUrl = url;

            // Create reinforcement button only when reinforcements spawn
            if (reinforcementButton == null)
            {
                // Remove placeholder and add reinforcement button
                buttonsPanel.remove(placeholderPanel);

                reinforcementButton = createLinkButton("Reinforcements");
                buttonsPanel.add(reinforcementButton);
                buttonsPanel.revalidate();
            }

            reinforcementButton.setEnabled(true);
            reinforcementButton.setForeground(Color.WHITE);

            // Remove old listeners and add new one
            for (var listener : reinforcementButton.getActionListeners())
            {
                reinforcementButton.removeActionListener(listener);
            }

            reinforcementButton.addActionListener(e -> openUrl(url));
        }

        private void openUrl(String url)
        {
            try
            {
                Desktop.getDesktop().browse(new URI(url));
            }
            catch (Exception ex)
            {
                log.warn("Unable to open URL {}", url, ex);
            }
        }
    }
}