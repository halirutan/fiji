package fiji.plugin.trackmate.action;

import ij.measure.ResultsTable;

import java.util.Collection;
import java.util.Set;

import javax.swing.ImageIcon;

import org.jgrapht.graph.DefaultWeightedEdge;

import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.TrackMateWizard;

public class ExportStatsToIJAction extends AbstractTMAction {

	public static final ImageIcon ICON = new ImageIcon(TrackMateWizard.class.getResource("images/calculator.png"));
	public static final String NAME = "Export statistics to tables";
	public static final String INFO_TEXT = "<html>" +
				"Compute and export all statistics to 3 ImageJ results table." +
				"Statistisc are separated in features computed for:" +
				"<ol>" +
				"	<li> spots in filtered tracks;" +
				"	<li> links between those spots;" +
				"	<li> filtered tracks." +
				"</ol>" +
				"For tracks and links, they are recalculated prior to exporting. Note " +
				"that spots and links that are not in a filtered tracks are not part" +
				"of this export." +
				"</html>";

	public ExportStatsToIJAction() {
		this.icon = ICON;
	}

	@Override
	public void execute(final TrackMate trackmate) {
		logger.log("Exporting statistics.\n");

		// Model
		final Model model = trackmate.getModel();
		final FeatureModel fm = model.getFeatureModel();

		// Export spots
		logger.log("  - Exporting spot statistics...");
		final Set<Integer> trackIDs = model.getTrackModel().trackIDs(true);
		final Collection<String> spotFeatures = trackmate.getModel().getFeatureModel().getSpotFeatures();

		// Create table
		final ResultsTable spotTable = new ResultsTable();

		// Parse spots to insert values as objects
		for (final Integer trackID : trackIDs) {
			final Set<Spot> track = model.getTrackModel().trackSpots(trackID);
			for (final Spot spot : track) {
				spotTable.incrementCounter();
				spotTable.addLabel(spot.getName());
				spotTable.addValue("ID", spot.ID());
				spotTable.addValue("TRACK_ID", trackID);
				for (final String feature : spotFeatures) {
					final Double val = spot.getFeature(feature);
					if (null == val) {
						continue;
					}
					spotTable.addValue(feature, val.doubleValue());
				}
			}
		}
		logger.log(" Done.\n");

		// Export edges
		logger.log("  - Exporting links statistics...");
		// Yield available edge feature
		final Collection<String> edgeFeatures = fm.getEdgeFeatures();

		// Create table
		final ResultsTable edgeTable = new ResultsTable();

		// Sort by track
		for (final Integer trackID : trackIDs) {

			final Set<DefaultWeightedEdge> track = model.getTrackModel().trackEdges(trackID);
			for (final DefaultWeightedEdge edge : track) {
				edgeTable.incrementCounter();
				edgeTable.addLabel(edge.toString());
				for(final String feature : edgeFeatures) {
					final Object o = fm.getEdgeFeature(edge, feature);
					if (o instanceof String) {
						continue;
					}
					final Number d = (Number) o;
					edgeTable.addValue(feature, d.doubleValue());
				}

			}
		}
		logger.log(" Done.\n");

		// Export tracks
		logger.log("  - Exporting tracks statistics...");
		// Yield available edge feature
		final Collection<String> trackFeatures = fm.getTrackFeatures();

		// Create table
		final ResultsTable trackTable = new ResultsTable();

		// Sort by track
		for (final Integer trackID : trackIDs) {
			trackTable.incrementCounter();
			trackTable.addLabel(model.getTrackModel().name(trackID));
			for (final String feature : trackFeatures) {
				final Double val = fm.getTrackFeature(trackID, feature);
				if (null == val) {
					System.out.println("Got a null feature value for feature " + feature + " on trackID " + trackID);
				} else {
					trackTable.addValue(feature, val);
				}
			}
		}
		logger.log(" Done.\n");

		// Show tables
		spotTable.show("Spots in tracks statistics");
		edgeTable.show("Links in tracks statistics");
		trackTable.show("Track statistics");
	}

	@Override
	public String getInfoText() {
		return INFO_TEXT;
	}

	@Override
	public String toString() {
		return NAME;
	}

}
