package gui;

import static java.util.Collections.sort;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.rmi.UnexpectedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class MainWindow {

	private static String OS = System.getProperty("os.name").toLowerCase();

	private final int LINES_IN_TEXT_AREA = 11;
	private final String CONFIGURATION_FILE_NAME = "config";
	private final String DEFAULT_LANGUAGE = "English";
	private final String DEFAULT_WORKING_DIRECTORY = "~";
	private final String LANGUAGES[] = { "English", "French", "German", "Italian", "Russian", "Spanish" };
	private final String FILTER_PREFIX = "filter";
	private final String TAGGER_PREFIX = "tagger";
	private final String TMP_PREFIX = "tmp";
	private final String TMP_DIR = "tmp";
	private final String LAST_WORKING_DIRECTORY = "lastWorkingDirectory";
	private final String LAST_USED_LANGUAGE = "lastUsedLanguage";

	private File originallyLoadedFile;
	private File currentlyProcessedFile;
	private String lastWorkingDirectory;
	private String language;
	private int currentTmp;
	private Map<String, String> filterMap;
	private Map<String, String> taggerMap;
	private boolean processingCancelled;

	private JFrame frmWordSieve;
	private JMenuBar menuBar;
	private JMenu mnFile;
	private JMenuItem mntmLoad;
	private JMenuItem mntmSaveAs;
	private JMenu mnLanguage;
	private JMenu mnAction;
	private JMenuItem mntmStripAnkiTranslations;
	private JMenuItem mntmCountSortWithStats;
	private JMenuItem mntmCountSortWithoutStats;
	private JMenuItem mntmUndo;
	private JMenu mnFilter;
	private JLabel lblLanguage;
	private JLabel lblFile;
	private JLabel lblSelectedLanguage;
	private JLabel lblSelectedFile;
	private JTextPane txtpnPreview;
	private JLabel lblPreview;
	private JMenuItem mntmFilter;
	private JMenu mnTagger;
	private JLabel lblTagger;
	private JLabel lblSelectedTagger;
	private JLabel lblFilter;
	private JLabel lblSelectedFilter;

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				MainWindow window = new MainWindow();
				window.frmWordSieve.setVisible(true);
			}
		});
	}

	public MainWindow() {
		try {
			Thread hook = new Thread() {
				public void run() {
					saveConfiguration();
				}
			};
			Runtime.getRuntime().addShutdownHook(hook);

			initializeUI();
			initializeVariables();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmWordSieve, ExceptionUtils.getStackTrace(e));
		}
	}

	private void initializeUI() {
		frmWordSieve = new JFrame();
		frmWordSieve.setTitle("Word Sieve");
		frmWordSieve.setBounds(100, 100, 535, 381);
		frmWordSieve.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		menuBar = new JMenuBar();
		frmWordSieve.setJMenuBar(menuBar);

		mnFile = new JMenu("File");
		menuBar.add(mnFile);

		mntmLoad = new JMenuItem("Load");
		mntmLoad.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(lastWorkingDirectory);
				int ret = chooser.showOpenDialog(frmWordSieve);
				if (ret == JFileChooser.APPROVE_OPTION) {
					currentlyProcessedFile = chooser.getSelectedFile();
					originallyLoadedFile = currentlyProcessedFile;
					lastWorkingDirectory = currentlyProcessedFile.getParentFile().getAbsolutePath();
					lblSelectedFile.setText(currentlyProcessedFile.getName());
					currentTmp = -1;
					mntmUndo.setEnabled(false);
					loadFileContentToPreview();
				}
			}
		});
		mnFile.add(mntmLoad);

		mntmSaveAs = new JMenuItem("Save As");
		mntmSaveAs.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser(lastWorkingDirectory);
				int ret = chooser.showSaveDialog(frmWordSieve);
				if (ret == JFileChooser.APPROVE_OPTION) {
					final File selected = chooser.getSelectedFile();
					lastWorkingDirectory = selected.getParentFile().getAbsolutePath();
					final JDialog dialog = constructDialog("Saving...");
					new SwingWorker<Void, Void>() {
						protected void done() {
							dialog.dispose();
						}

						protected Void doInBackground() throws Exception {
							saveResultsToFile(selected);
							return null;
						}
					}.execute();
					dialog.setVisible(true);
				}
			}
		});
		mnFile.add(mntmSaveAs);

		mnLanguage = new JMenu("Language");
		menuBar.add(mnLanguage);

		for (int i = 0; i < LANGUAGES.length; i++) {
			final JRadioButtonMenuItem item = new JRadioButtonMenuItem(LANGUAGES[i]);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					switchLanguageTo(item.getText());
				}
			});
			mnLanguage.add(item);
		}

		mnAction = new JMenu("Action");
		menuBar.add(mnAction);

		mntmStripAnkiTranslations = new JMenuItem("Strip Anki translations");
		mntmStripAnkiTranslations.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JDialog dialog = constructDialog("Computing...");
				new SwingWorker<Void, Void>() {
					protected void done() {
						dialog.dispose();
					}

					protected Void doInBackground() throws Exception {
						stripAnkiTranslations();
						return null;
					}
				}.execute();
				dialog.setVisible(true);
			}
		});
		mnAction.add(mntmStripAnkiTranslations);

		mntmCountSortWithStats = new JMenuItem("Count&Sort with stats");
		mntmCountSortWithStats.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JDialog dialog = constructDialog("Computing...");
				new SwingWorker<Void, Void>() {
					protected void done() {
						dialog.dispose();
					}

					protected Void doInBackground() throws Exception {
						countSortWithStats();
						return null;
					}
				}.execute();
				dialog.setVisible(true);
			}
		});
		mnAction.add(mntmCountSortWithStats);

		mntmCountSortWithoutStats = new JMenuItem("Count&Sort without stats");
		mntmCountSortWithoutStats.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JDialog dialog = constructDialog("Computing...");
				new SwingWorker<Void, Void>() {
					protected void done() {
						dialog.dispose();
					}

					protected Void doInBackground() throws Exception {
						countSortWithoutStats();
						return null;
					}
				}.execute();
				dialog.setVisible(true);
			}
		});
		mnAction.add(mntmCountSortWithoutStats);

		mntmFilter = new JMenuItem("Filter");
		mntmFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JDialog dialog = constructDialog("Computing...");
				new SwingWorker<Void, Void>() {
					protected void done() {
						dialog.dispose();
					}

					protected Void doInBackground() throws Exception {
						filter();
						return null;
					}
				}.execute();
				dialog.setVisible(true);
			}
		});
		mnAction.add(mntmFilter);

		mntmUndo = new JMenuItem("Undo last action");
		mntmUndo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				currentTmp--;
				if (currentTmp >= 0) {
					currentlyProcessedFile = new File(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
				} else {
					currentlyProcessedFile = originallyLoadedFile;
					mntmUndo.setEnabled(false);
				}
				loadFileContentToPreview();
			}
		});
		mntmUndo.setEnabled(false);
		mnAction.add(mntmUndo);

		mnFilter = new JMenu("Filter");
		menuBar.add(mnFilter);

		for (String s : LANGUAGES) {
			final JMenuItem item = new JMenuItem(s);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JFileChooser chooser = new JFileChooser(lastWorkingDirectory);
					int ret = chooser.showOpenDialog(frmWordSieve);
					if (ret == JFileChooser.APPROVE_OPTION) {
						File selected = chooser.getSelectedFile();
						filterMap.put(item.getText(), selected.getAbsolutePath());
						lastWorkingDirectory = selected.getParentFile().getAbsolutePath();
						lblSelectedFilter.setText(selected.getName());
					}
				}
			});
			mnFilter.add(item);
		}

		mnTagger = new JMenu("Tagger");
		menuBar.add(mnTagger);

		for (String s : LANGUAGES) {
			final JMenuItem item = new JMenuItem(s);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JFileChooser chooser = new JFileChooser(lastWorkingDirectory);
					int ret = chooser.showOpenDialog(frmWordSieve);
					if (ret == JFileChooser.APPROVE_OPTION) {
						File selected = chooser.getSelectedFile();
						taggerMap.put(item.getText(), selected.getAbsolutePath());
						lastWorkingDirectory = selected.getParentFile().getAbsolutePath();
						lblSelectedTagger.setText(selected.getName());
					}
				}
			});
			mnTagger.add(item);
		}

		frmWordSieve.getContentPane().setLayout(null);

		lblLanguage = new JLabel("Language:");
		lblLanguage.setBounds(12, 39, 80, 15);
		frmWordSieve.getContentPane().add(lblLanguage);

		lblFile = new JLabel("File:");
		lblFile.setBounds(12, 12, 80, 15);
		frmWordSieve.getContentPane().add(lblFile);

		lblSelectedLanguage = new JLabel("");
		lblSelectedLanguage.setBounds(94, 39, 419, 15);
		frmWordSieve.getContentPane().add(lblSelectedLanguage);

		lblSelectedFile = new JLabel("");
		lblSelectedFile.setBounds(94, 12, 419, 15);
		frmWordSieve.getContentPane().add(lblSelectedFile);

		txtpnPreview = new JTextPane();
		txtpnPreview.setEditable(false);
		txtpnPreview.setBounds(12, 147, 501, 171);
		frmWordSieve.getContentPane().add(txtpnPreview);

		lblPreview = new JLabel("Preview:");
		lblPreview.setBounds(12, 120, 80, 15);
		frmWordSieve.getContentPane().add(lblPreview);

		lblTagger = new JLabel("Tagger:");
		lblTagger.setBounds(12, 66, 80, 15);
		frmWordSieve.getContentPane().add(lblTagger);

		lblSelectedTagger = new JLabel("");
		lblSelectedTagger.setBounds(94, 66, 419, 15);
		frmWordSieve.getContentPane().add(lblSelectedTagger);

		lblFilter = new JLabel("Filter:");
		lblFilter.setBounds(12, 93, 80, 15);
		frmWordSieve.getContentPane().add(lblFilter);

		lblSelectedFilter = new JLabel("");
		lblSelectedFilter.setBounds(94, 93, 419, 15);
		frmWordSieve.getContentPane().add(lblSelectedFilter);
	}

	private void initializeVariables() throws Exception {
		filterMap = new HashMap<String, String>();
		taggerMap = new HashMap<String, String>();
		currentlyProcessedFile = null;
		lastWorkingDirectory = DEFAULT_WORKING_DIRECTORY;
		String tmpLanguage = DEFAULT_LANGUAGE;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(
					CONFIGURATION_FILE_NAME))));
			String line;

			while ((line = in.readLine()) != null) {
				String chunk[] = line.split("=");
				if (chunk.length != 2) {
					System.out.println("zle sformatowane");
					continue;
				}
				if (chunk[0].equals(LAST_WORKING_DIRECTORY))
					lastWorkingDirectory = chunk[1];
				else if (chunk[0].equals(LAST_USED_LANGUAGE))
					tmpLanguage = chunk[1];
				else if (chunk[0].startsWith(FILTER_PREFIX))
					filterMap.put(chunk[0].substring(FILTER_PREFIX.length()), chunk[1]);
				else if (chunk[0].startsWith(TAGGER_PREFIX))
					taggerMap.put(chunk[0].substring(TAGGER_PREFIX.length()), chunk[1]);
			}
			switchLanguageTo(tmpLanguage);
			in.close();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmWordSieve, "Could not find 'config' file. Running with default settings.");
		}
	}

	private void loadFileContentToPreview() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(
					currentlyProcessedFile))));
			String line;
			String buffer = "";
			for (int i = 0; i < LINES_IN_TEXT_AREA; i++) {
				if ((line = in.readLine()) == null)
					break;
				buffer = buffer + line + "\n";
			}
			txtpnPreview.setText(buffer);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frmWordSieve, "Error loading preview: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private void switchLanguageTo(String l) {
		language = l;
		for (int i = 0; i < mnLanguage.getItemCount(); i++)
			mnLanguage.getItem(i).setSelected(mnLanguage.getItem(i).getText().equals(l));
		lblSelectedLanguage.setText(l);
		if (filterMap.get(l) != null)
			lblSelectedFilter.setText(new File(filterMap.get(l)).getName());
		else
			lblSelectedFilter.setText("");
		if (taggerMap.get(l) != null)
			lblSelectedTagger.setText(new File(taggerMap.get(l)).getName());
		else
			lblSelectedTagger.setText("");
	}

	private void saveConfiguration() {
		try {
			PrintWriter out = new PrintWriter(new FileWriter(CONFIGURATION_FILE_NAME));

			out.println(LAST_WORKING_DIRECTORY + "=" + lastWorkingDirectory);
			out.println(LAST_USED_LANGUAGE + "=" + language);
			for (Map.Entry<String, String> entry : filterMap.entrySet())
				out.println(FILTER_PREFIX + entry.getKey() + "=" + entry.getValue());
			for (Map.Entry<String, String> entry : taggerMap.entrySet())
				out.println(TAGGER_PREFIX + entry.getKey() + "=" + entry.getValue());

			out.close();
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frmWordSieve, ex.getMessage());
			throw new RuntimeException(ex);
		}
	}

	private void stripAnkiTranslations() throws Exception {
		try {
			currentTmp++;
			BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(
					currentlyProcessedFile))));
			PrintWriter out = new PrintWriter(new FileWriter(TMP_DIR + "/" + TMP_PREFIX + currentTmp));
			String line;

			while ((line = in.readLine()) != null && !processingCancelled) {
				String[] splitted = line.split("\t");
				String s = splitted[0];
				out.println(s);
			}
			in.close();
			out.close();

			if (processingCancelled) {
				currentTmp--;
			} else {
				currentlyProcessedFile = new File(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
				loadFileContentToPreview();
				mntmUndo.setEnabled(true);
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frmWordSieve, ex.getMessage());
			throw ex;
		}
	}

	private JDialog constructDialog(String message) {
		processingCancelled = false;
		final JDialog dialog = new JDialog(frmWordSieve, true);
		dialog.setLocationRelativeTo(frmWordSieve);
		dialog.setMinimumSize(new Dimension(200, 70));
		dialog.getContentPane().setLayout(new GridLayout(2, 1));
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		JLabel label = new JLabel(message);
		JButton button = new JButton("Cancel");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				processingCancelled = true;
				dialog.dispose();
			}
		});
		dialog.getContentPane().add(label);
		dialog.getContentPane().add(button);
		return dialog;
	}

	private void saveResultsToFile(File file) throws Exception {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(
					currentlyProcessedFile))));
			PrintWriter out = new PrintWriter(new FileWriter(file));
			String line;

			while ((line = in.readLine()) != null && !processingCancelled) {
				out.println(line);
			}
			in.close();
			out.close();
			if (processingCancelled) {
				file.delete();
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frmWordSieve, ex.getMessage());
			throw ex;
		}
	}

	private Process executeTagger() throws IOException {
		if (language == null) {
			JOptionPane.showMessageDialog(frmWordSieve, "Choose the appropriate language!");
			throw new AssertionError("Language not selected");
		}
		if (isUnix()) {
			return Runtime.getRuntime().exec(taggerMap.get(language) + " " + currentlyProcessedFile.getAbsolutePath());
		} else if (isWindows()) {
			return Runtime.getRuntime().exec(
					"cmd.exe /c tag-" + language.toLowerCase() + " " + currentlyProcessedFile.getAbsolutePath());
		} else {
			throw new UnexpectedException("Could not recognize the operating system");
		}
	}

	private PrintWriter getWriterToTmp(String path) throws IOException {
		File f = new File(TMP_DIR);
		if (!f.isDirectory()) {
			if (f.exists()) {
				throw new IOException("'" + TMP_DIR + "' directory is needed but such file exists");
			} else {
				f.mkdirs();
			}
		}
		return new PrintWriter(new FileWriter(path));
	}

	private void countSortWithStats() throws Exception {
		if (currentlyProcessedFile.getAbsolutePath().contains(" ")) {
			JOptionPane.showMessageDialog(frmWordSieve, "Cannot process files that have spaces in their path");
			return;
		}
		try {
			currentTmp++;
			Process p = executeTagger();
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			PrintWriter out = getWriterToTmp(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
			String line;
			List<Stat> list = new ArrayList<Stat>();

			while ((line = in.readLine()) != null && !processingCancelled) {
				String[] splitted = line.split("\t");
				String s = splitted[splitted.length - 1];
				addWordToList(s, list);
			}
			p.waitFor();

			sort(list, new MyComparator());

			int i = 1;
			for (Stat s : list) {
				out.println((i++) + " " + s.word + " " + s.occurrences);
			}
			in.close();
			out.close();

			if (processingCancelled) {
				currentTmp--;
			} else {
				currentlyProcessedFile = new File(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
				loadFileContentToPreview();
				mntmUndo.setEnabled(true);
			}
		} catch (Exception ex) {
			throw ex;
		}
	}

	private void countSortWithoutStats() throws Exception {
		if (currentlyProcessedFile.getAbsolutePath().contains(" ")) {
			JOptionPane.showMessageDialog(frmWordSieve, "Cannot process files that have spaces in their path");
			return;
		}
		try {
			currentTmp++;
			Process p = executeTagger();
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			PrintWriter out = getWriterToTmp(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
			String line;
			List<Stat> list = new ArrayList<Stat>();

			while ((line = in.readLine()) != null && !processingCancelled) {
				String[] splitted = line.split("\t");
				String s = splitted[splitted.length - 1];
				addWordToList(s, list);
			}
			p.waitFor();

			sort(list, new MyComparator());

			for (Stat s : list) {
				out.println(s.word);
			}
			in.close();
			out.close();

			if (processingCancelled) {
				currentTmp--;
			} else {
				currentlyProcessedFile = new File(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
				loadFileContentToPreview();
				mntmUndo.setEnabled(true);
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frmWordSieve, ex.getMessage());
			throw ex;
		}
	}

	private void filter() throws Exception {
		if (language == null) {
			JOptionPane.showMessageDialog(frmWordSieve, "Choose the appropriate language!");
			throw new AssertionError("Language not selected");
		}
		if (!filterMap.containsKey(language)) {
			JOptionPane.showMessageDialog(frmWordSieve, "No filter file was specified for this language!");
			throw new AssertionError("Filter file not specified");
		}
		try {
			currentTmp++;
			BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(
					currentlyProcessedFile))));
			BufferedReader inFilter = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(
					filterMap.get(language)))));
			PrintWriter out = new PrintWriter(new FileWriter(TMP_DIR + "/" + TMP_PREFIX + currentTmp));
			String line;
			List<String> filterList = new ArrayList<String>();

			while ((line = inFilter.readLine()) != null && !processingCancelled) {
				filterList.add(line);
			}

			while ((line = in.readLine()) != null && !processingCancelled) {
				String[] splitted = line.split(" ");
				String s;
				if (splitted.length == 1)
					s = splitted[0];
				else if (splitted.length == 3)
					s = splitted[1];
				else
					continue;
				if (filterList.contains(s))
					continue;
				out.println(line);
			}
			in.close();
			inFilter.close();
			out.close();

			if (processingCancelled) {
				currentTmp--;
			} else {
				currentlyProcessedFile = new File(TMP_DIR + "/" + TMP_PREFIX + currentTmp);
				loadFileContentToPreview();
				mntmUndo.setEnabled(true);
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frmWordSieve, ex.getMessage());
			throw ex;
		}
	}

	private void addWordToList(String word, List<Stat> list) {
		for (Stat s : list) {
			if (s.word.equals(word)) {
				s.occurrences++;
				return;
			}
		}
		list.add(new Stat(word, 1));
	}

	private static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	private static boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	private class Stat {
		String word;
		int occurrences;

		public Stat(String word, int occurrences) {
			this.word = word;
			this.occurrences = occurrences;
		}
	}

	private class MyComparator implements Comparator<Stat> {
		public int compare(Stat s1, Stat s2) {
			return s2.occurrences - s1.occurrences;
		}

		public boolean equals(Object obj) {
			return false;
		}
	}
}
