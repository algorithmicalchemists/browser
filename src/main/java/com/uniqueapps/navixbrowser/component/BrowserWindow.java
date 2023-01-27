package com.uniqueapps.navixbrowser.component;

import com.uniqueapps.navixbrowser.Main;
import com.uniqueapps.navixbrowser.handler.*;
import com.uniqueapps.navixbrowser.listener.NavixComponentListener;
import com.uniqueapps.navixbrowser.listener.NavixWindowListener;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BrowserWindow extends JFrame {

	private static final long serialVersionUID = -3658310837225120769L;

	static GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0];

	public final CefApp cefApp;
	private final CefClient cefClient;
	private final JTextField browserAddressField;
	private final JButton homeButton;
	private final JButton forwardNav;
	private final JButton backwardNav;
	private final JButton reloadButton;
	private final JButton addTabButton;
	private final JButton addBookmarkButton;
	private final JButton contextMenuButton;
	public final JProgressBar loadBar;
	public final BrowserTabbedPane tabbedPane;
	public final JSplitPane splitPane;
	public boolean browserIsInFocus = false;

	File bookmarkFile = new File(Main.userAppData, "bookmarks");
	private final Map<String, String> bookmarks = new HashMap<>();
	JPanel bookmarksPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 3));

	@SuppressWarnings("unchecked")
	public BrowserWindow(String startURL, boolean useOSR, boolean isTransparent, CefApp cefAppX)
			throws IOException, UnsupportedPlatformException, InterruptedException, CefInitializationException {

		File resources = new File(".", "resources");
		if (resources.mkdirs()) {
			Files.copy(getClass().getResourceAsStream("/resources/navix.ico"),
					new File(new File(".", "resources"), "navix.ico").toPath());
			Files.copy(getClass().getResourceAsStream("/resources/newtab-dark.html"),
					new File(new File(".", "resources"), "newtab-dark.html").toPath());
			Files.copy(getClass().getResourceAsStream("/resources/style-dark.css"),
					new File(new File(".", "resources"), "style-dark.css").toPath());
			Files.copy(getClass().getResourceAsStream("/resources/newtab-light.html"),
					new File(new File(".", "resources"), "newtab-light.html").toPath());
			Files.copy(getClass().getResourceAsStream("/resources/style-light.css"),
					new File(new File(".", "resources"), "style-light.css").toPath());
		}

		cefApp = cefAppX;
		cefClient = cefApp.createClient();

		bookmarkFile.createNewFile();

		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(bookmarkFile))) {
			bookmarks.putAll((HashMap<String, String>) ois.readObject());
		} catch (Exception e) {
			e.printStackTrace();
			refreshBookmarks();
		}

		try {
			setIconImage(ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/navix.png"))));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		homeButton = new JButton();
		backwardNav = new JButton();
		forwardNav = new JButton();
		reloadButton = new JButton();
		addTabButton = new JButton();
		addBookmarkButton = new JButton();
		contextMenuButton = new JButton();
		loadBar = new JProgressBar();
		splitPane = new JSplitPane();
		browserAddressField = new JTextField(100) {
			private static final long serialVersionUID = -6518323374167056051L;

			@Override
			protected void paintComponent(Graphics g) {
				Map<RenderingHints.Key, Object> rh = new HashMap<>();
				rh.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
				rh.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				rh.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				Graphics2D g2d = (Graphics2D) g;
				g2d.setRenderingHints(rh);
				super.paintComponent(g2d);
			}
		};

		try {
			tabbedPane = new BrowserTabbedPane(this, homeButton, forwardNav, backwardNav, reloadButton,
					browserAddressField);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Main.downloadWindow.setVisible(false);

		addCefHandlers();
		addListeners();
		prepareNavBar(startURL, useOSR, isTransparent);

		tabbedPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		tabbedPane.addBrowserTab(cefApp, startURL, useOSR, isTransparent);
	}

	private void addCefHandlers() {
		cefClient.addContextMenuHandler(new NavixContextMenuHandler(cefApp, this));
		cefClient.addDialogHandler(new NavixDialogHandler(this));
		cefClient.addDisplayHandler(new NavixDisplayHandler(this, tabbedPane, browserAddressField, cefApp));
		cefClient.addDownloadHandler(new NavixDownloadHandler(this));
		cefClient.addFocusHandler(new NavixFocusHandler(this));
		cefClient.addLoadHandler(new NavixLoadHandler(forwardNav, backwardNav, this));
	}

	private void addListeners() {
		// A hack to enable browser resizing in non-OSR mode in Linux.
		addComponentListener(new NavixComponentListener(this, tabbedPane));
		addWindowListener(new NavixWindowListener(this, cefApp));
	}

	private void prepareNavBar(String startURL, boolean useOSR, boolean isTransparent) {
		browserAddressField.addActionListener(l -> {
			String query = browserAddressField.getText();
			try {
				new URL(query);
				tabbedPane.getSelectedBrowser().loadURL(query);
			} catch (MalformedURLException e) {
				if (query.contains(".") || query.contains("://")) {
					tabbedPane.getSelectedBrowser().loadURL(query);
				} else {
					tabbedPane.getSelectedBrowser().loadURL(Main.settings.searchEngine + query);
				}
			}
		});
		browserAddressField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (!browserIsInFocus)
					return;
				browserIsInFocus = false;
				KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
				browserAddressField.requestFocusInWindow();
			}
		});
		browserAddressField.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				browserAddressField.selectAll();
			}
		});

		browserAddressField.setBorder(new RoundedBorder(Color.LIGHT_GRAY.darker(), 1, 28, 5));
		browserAddressField.setBackground(new Color(0x0, true));
		browserAddressField.setFont(new JLabel().getFont());

		backwardNav.setEnabled(false);
		forwardNav.setEnabled(false);

		try {
			homeButton.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/home.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
			backwardNav.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/left-arrow.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
			forwardNav.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/right-arrow.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
			reloadButton.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/reload.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
			addTabButton.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/add.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
			addBookmarkButton.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/bookmark.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
			contextMenuButton.setIcon(new ImageIcon(
					ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/menu-bar.png")))
							.getScaledInstance(22, 22, BufferedImage.SCALE_SMOOTH)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		homeButton.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				tabbedPane.getSelectedBrowser().loadURL("navix://home");
			}
		});
		backwardNav.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				if (tabbedPane.getSelectedBrowser().canGoBack()) {
					tabbedPane.getSelectedBrowser().goBack();
				}
			}
		});
		forwardNav.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				if (tabbedPane.getSelectedBrowser().canGoForward()) {
					tabbedPane.getSelectedBrowser().goForward();
				}
			}
		});
		reloadButton.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				tabbedPane.getSelectedBrowser().reload();
			}
		});
		addTabButton.addActionListener(l -> tabbedPane.addBrowserTab(cefApp, startURL, useOSR, isTransparent));
		addBookmarkButton.addActionListener(l -> {
			String name = JOptionPane.showInputDialog("Bookmark name", "New Bookmark");
			String url = JOptionPane.showInputDialog("URL",
					tabbedPane.getSelectedBrowser() != null ? tabbedPane.getSelectedBrowser().getURL()
							: "https://google.com/");
			if (url != null && name != null) {
				bookmarks.put(name, url);
				refreshBookmarks();
			}
		});
		contextMenuButton.addActionListener(l -> {
			JPopupMenu popup = new JPopupMenu();

			JMenuItem newTab = new JMenuItem("New tab");
			newTab.addActionListener(l1 -> tabbedPane.addBrowserTab(cefApp, startURL, useOSR, isTransparent));
			popup.add(newTab);

			JMenuItem downloads = new JMenuItem("Downloads");
			downloads.addActionListener(l1 -> tabbedPane.addDownloadsTab(cefApp));
			popup.add(downloads);

			JMenuItem settings = new JMenuItem("Settings");
			settings.addActionListener(l1 -> tabbedPane.addSettingsTab(cefApp));
			popup.add(settings);

			popup.addSeparator();

			JMenuItem toggleFullscreen = new JMenuItem("Toggle fullscreen");
			toggleFullscreen.addActionListener(l1 -> {
				if (device.getFullScreenWindow() != BrowserWindow.this) {
					BrowserWindow.this.dispose();
					setUndecorated(true);
					setExtendedState(JFrame.MAXIMIZED_BOTH);
					setVisible(true);
					device.setFullScreenWindow(BrowserWindow.this);
				} else {
					BrowserWindow.this.dispose();
					setUndecorated(false);
					setVisible(true);
					device.setFullScreenWindow(null);
				}
			});
			popup.add(toggleFullscreen);

			JMenuItem toggleInspector = new JMenuItem("Toggle inspector");
			toggleInspector.addActionListener(l1 -> {
				if (splitPane.getRightComponent() == null) {
					if (tabbedPane.getSelectedBrowser() != null) {
						splitPane.setRightComponent(tabbedPane.getSelectedBrowser().getDevTools().getUIComponent());
						splitPane.setDividerLocation(1000);
					} else {
						JOptionPane.showMessageDialog(this, "Cannot open inspector for current tab!");
					}
				} else {
					splitPane.setRightComponent(null);
				}
			});
			popup.add(toggleInspector);

			popup.addSeparator();

			JMenuItem exit = new JMenuItem("Exit");
			exit.addActionListener(l1 -> System.exit(0));
			popup.add(exit);

			popup.show(this, this.getWidth(), 0);
		});

		JPanel separatorPanel = new JPanel() {
			private static final long serialVersionUID = -1266847953751569210L;

			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(Color.DARK_GRAY.brighter());
				g.drawLine(getWidth() / 2, 3, getWidth() / 2, getHeight() - 3);
			}
		};

		JPanel navBar = new JPanel(new GridBagLayout());

		for (var bookmark : bookmarks.entrySet()) {
			JButton bookmarkButton = new JButton(bookmark.getKey()) {
				private static final long serialVersionUID = 7012838912951844369L;

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					super.paintComponent(g2d);
				}
			};
			bookmarkButton.setBorder(new EmptyBorder(4, 4, 4, 4));
			bookmarkButton.setBackground(this.getBackground());
			try {
				bookmarkButton.setIcon(new ImageIcon(
						ImageIO.read(new URL("https://www.google.com/s2/favicons?domain=" + bookmark.getValue()))));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			bookmarkButton.addActionListener(l -> tabbedPane.getSelectedBrowser().loadURL(bookmark.getValue()));
			JPopupMenu popup = new JPopupMenu();
			JMenuItem removeBookmark = new JMenuItem("Remove Bookmark");
			removeBookmark.addActionListener(l -> {
				bookmarks.remove(bookmark.getKey());
				refreshBookmarks();
			});
			popup.add(removeBookmark);
			bookmarkButton.setComponentPopupMenu(popup);
			bookmarksPanel.add(bookmarkButton);
		}
		bookmarksPanel.setVisible(!bookmarks.isEmpty());

		loadBar.setVisible(false);
		loadBar.setIndeterminate(true);
		JPanel bottomPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbcX = new GridBagConstraints();
		gbcX.fill = GridBagConstraints.HORIZONTAL;
		gbcX.weightx = 8;
		bottomPanel.add(loadBar, gbcX);
		gbcX.gridy = 1;
		gbcX.weightx = 10;
		bottomPanel.add(bookmarksPanel, gbcX);

		JToolBar toolBar = new JToolBar();
		toolBar.setMargin(new Insets(3, 2, 3, 2));
		toolBar.add(addTabButton);
		toolBar.add(separatorPanel);
		toolBar.add(backwardNav);
		toolBar.add(forwardNav);
		toolBar.add(reloadButton);
		toolBar.add(homeButton);

		JToolBar toolBar2 = new JToolBar();
		toolBar.setMargin(new Insets(3, 3, 3, 3));
		toolBar2.add(addBookmarkButton);
		toolBar2.add(contextMenuButton);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.weightx = 1;
		navBar.add(toolBar, gbc);
		gbc.weightx = 100;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		navBar.add(browserAddressField, gbc);
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.NONE;
		navBar.add(toolBar2, gbc);

		int defaultSize = splitPane.getDividerSize();
		splitPane.addContainerListener(new ContainerAdapter() {
			@Override
			public void componentAdded(ContainerEvent e) {
				super.componentAdded(e);
				if (splitPane.getRightComponent() == null) {
					splitPane.setDividerSize(0);
				} else {
					splitPane.setDividerSize(defaultSize);
				}
			}

			@Override
			public void componentRemoved(ContainerEvent e) {
				super.componentRemoved(e);
				if (splitPane.getRightComponent() == null) {
					splitPane.setDividerSize(0);
				} else {
					splitPane.setDividerSize(defaultSize);
				}
			}
		});
		splitPane.setLeftComponent(tabbedPane);
		splitPane.setRightComponent(null);

		getContentPane().add(navBar, BorderLayout.NORTH);
		getContentPane().add(splitPane, BorderLayout.CENTER);
		getContentPane().add(bottomPanel, BorderLayout.SOUTH);
	}

	private void refreshBookmarks() {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(bookmarkFile))) {
			oos.writeObject(bookmarks);
		} catch (IOException e) {
			e.printStackTrace();
		}
		bookmarksPanel.removeAll();
		for (var bookmark : bookmarks.entrySet()) {
			JButton bookmarkButton = new JButton(bookmark.getKey()) {
				private static final long serialVersionUID = 6390725135135905940L;

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					super.paintComponent(g2d);
				}
			};
			bookmarkButton.setBorder(new EmptyBorder(4, 4, 4, 4));
			bookmarkButton.setBackground(this.getBackground());
			try {
				bookmarkButton.setIcon(new ImageIcon(
						ImageIO.read(new URL("https://www.google.com/s2/favicons?domain=" + bookmark.getValue()))));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			bookmarkButton.addActionListener(l -> tabbedPane.getSelectedBrowser().loadURL(bookmark.getValue()));
			JPopupMenu popup = new JPopupMenu();
			JMenuItem removeBookmark = new JMenuItem("Remove Bookmark");
			removeBookmark.addActionListener(l -> {
				bookmarks.remove(bookmark.getKey());
				refreshBookmarks();
			});
			popup.add(removeBookmark);
			bookmarkButton.setComponentPopupMenu(popup);
			bookmarksPanel.add(bookmarkButton);
		}
		if (!bookmarks.isEmpty()) {
			bookmarksPanel.setVisible(false);
			bookmarksPanel.setVisible(true);
		} else {
			bookmarksPanel.setVisible(false);
		}
	}
}
