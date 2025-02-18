package org.openstreetmap.josm.plugins.elevalidator;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.MultiMap;

/**
 * Tests if there are duplicate elevators
 */
public class DuplicateElevator extends Test {

    /**
     * Class to store a way reduced to coordinates and keys. Essentially this is used to call the
     * <code>equals{}</code> function.
     */
    private static class WayPair {
        private final List<LatLon> coor;
        private final Map<String, String> keys;

        WayPair(List<LatLon> coor, Map<String, String> keys) {
            this.coor = coor;
            this.keys = keys;
        }

        @Override
        public int hashCode() {
            return Objects.hash(coor, keys);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            WayPair wayPair = (WayPair) obj;
            return Objects.equals(coor, wayPair.coor) &&
                    Objects.equals(keys, wayPair.keys);
        }
    }

    /**
     * Class to store a way reduced to coordinates. Essentially this is used to call the
     * <code>equals{}</code> function.
     */
    private static class WayPairNoTags {
        private final List<LatLon> coor;

        WayPairNoTags(List<LatLon> coor) {
            this.coor = coor;
        }

        @Override
        public int hashCode() {
            return Objects.hash(coor);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            WayPairNoTags that = (WayPairNoTags) obj;
            return Objects.equals(coor, that.coor);
        }
    }

    /** Test identification for exactly identical ways (coordinates and tags). */
    protected static final int DUPLICATE_ELEVATOR = 1401;
    /** Test identification for identical ways (coordinates only). */
    protected static final int SAME_ELEVATOR = 1402;

    /** Bag of all ways */
    private MultiMap<WayPair, OsmPrimitive> ways;

    /** Bag of all ways, regardless of tags */
    private MultiMap<WayPairNoTags, OsmPrimitive> waysNoTags;

    /** Set of known hashcodes for list of coordinates **/
    private Set<Integer> knownHashCodes;

    /**
     * Constructor
     */
    public DuplicateElevator() {
        super(tr("Duplicated elevators"),
                tr("This test checks that there are no elevators with same node coordinates and different level tags."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        ways = new MultiMap<>(1000);
        waysNoTags = new MultiMap<>(1000);
        knownHashCodes = new HashSet<>(1000);
    }

    @Override
    public void endTest() {
        super.endTest();
        for (Set<OsmPrimitive> duplicated : ways.values()) {
            if (duplicated.size() > 1) {
                TestError testError = TestError.builder(this, Severity.ERROR, DUPLICATE_ELEVATOR)
                        .message(tr("Duplicated elevators"))
                        .primitives(duplicated)
                        .build();
                errors.add(testError);
            }
        }

        for (Set<OsmPrimitive> sameway : waysNoTags.values()) {
            if (sameway.size() > 1) {
                //Report error only if at least some tags are different, as otherwise the error was already reported as duplicated ways
                Map<String, String> tags0 = null;
                boolean skip = true;

                for (OsmPrimitive o : sameway) {
                    if (tags0 == null) {
                        tags0 = o.getKeys();
                        removeUninterestingKeys(tags0);
                    } else {
                        Map<String, String> tagsCmp = o.getKeys();
                        removeUninterestingKeys(tagsCmp);
                        if (!tagsCmp.equals(tags0)) {
                            skip = false;
                            break;
                        }
                    }
                }
                if (skip) {
                    continue;
                }
                TestError testError = TestError.builder(this, Severity.WARNING, SAME_ELEVATOR)
                        .message(tr("Elevators with same position"))
                        .primitives(sameway)
                        .build();
                errors.add(testError);
            }
        }
        ways = null;
        waysNoTags = null;
        knownHashCodes = null;
    }

    /**
     * Remove uninteresting discardable keys to normalize the tags
     * @param wkeys The tags of the way, obtained by {@code Way#getKeys}
     */
    public void removeUninterestingKeys(Map<String, String> wkeys) {
        for (String key : AbstractPrimitive.getDiscardableKeys()) {
            wkeys.remove(key);
        }
    }

    @Override
    public void visit(Way w) {
        if (!w.isUsable())
            return;
        List<LatLon> wLat = getOrderedNodes(w);
        // If this way has not direction-dependant keys, make sure the list is ordered the same for all ways (fix #8015)
        if (!w.hasDirectionKeys()) {
            int hash = wLat.hashCode();
            if (!knownHashCodes.contains(hash)) {
                List<LatLon> reversedwLat = new ArrayList<>(wLat);
                Collections.reverse(reversedwLat);
                int reverseHash = reversedwLat.hashCode();
                if (!knownHashCodes.contains(reverseHash)) {
                    // Neither hash or reversed hash is known, remember hash
                    knownHashCodes.add(hash);
                } else {
                    // Reversed hash is known, use the reverse list then
                    wLat = reversedwLat;
                }
            }
        }
        // Check if the way has the highway=elevator tag
        if (w.hasTag("highway","elevator")) {

            Map<String, String> wkeys = w.getKeys();
            removeUninterestingKeys(wkeys);
            WayPair wKey = new WayPair(wLat, wkeys);
            ways.put(wKey, w);
            WayPairNoTags wKeyN = new WayPairNoTags(wLat);
            waysNoTags.put(wKeyN, w);
        }

        else {
            return;
        }

    }

    /**
     * Replies the ordered list of nodes of way w such as it is easier to find duplicated ways.
     * In case of a closed way, build the list of lat/lon starting from the node with the lowest id
     * to ensure this list will produce the same hashcode as the list obtained from another closed
     * way with the same nodes, in the same order, but that does not start from the same node (fix #8008)
     * @param w way
     * @return the ordered list of nodes of way w such as it is easier to find duplicated ways
     * @since 7721
     */
    public static List<LatLon> getOrderedNodes(Way w) {
        List<Node> wNodes = w.getNodes();                        // The original list of nodes for this way
        List<Node> wNodesToUse = new ArrayList<>(wNodes.size()); // The list that will be considered for this test
        if (w.isClosed()) {
            int lowestIndex = 0;
            long lowestNodeId = wNodes.get(0).getUniqueId();
            for (int i = 1; i < wNodes.size(); i++) {
                if (wNodes.get(i).getUniqueId() < lowestNodeId) {
                    lowestNodeId = wNodes.get(i).getUniqueId();
                    lowestIndex = i;
                }
            }
            IntStream.range(lowestIndex, wNodes.size() - 1)
                    .mapToObj(wNodes::get)
                    .forEach(wNodesToUse::add);
            for (int i = 0; i < lowestIndex; i++) {
                wNodesToUse.add(wNodes.get(i));
            }
            wNodesToUse.add(wNodes.get(lowestIndex));
        } else {
            wNodesToUse.addAll(wNodes);
        }
        // Build the list of lat/lon

        return wNodesToUse.stream()
                .map(Node::getCoor)
                .collect(Collectors.toList());
    }

    /**
     * Fix the error by removing all but one instance of duplicate elevator highways
     */
    @Override
    public Command fixError(TestError testError) {
        Set<Way> wayz = testError.primitives(Way.class)
                .filter(w -> !w.isDeleted())
                .collect(Collectors.toSet());

        if (wayz.size() < 2)
            return null;

        long idToKeep = 0;
        Way wayToKeep = wayz.iterator().next();
        // Find the way that is member of one or more relations. (If any)
        Way wayWithRelations = null;
        List<Relation> relations = null;
        String newLevelsTag = "";
        StringBuffer sb = new StringBuffer();
        ArrayList<String> elevatorLevels = new ArrayList<String>();
        for (Way w : wayz) {
            // --- own code to store the level tag ---
            Map<String, String> elevatorKeys = w.getKeys();
            String elevatorLevel = elevatorKeys.get("level");

            elevatorLevels.add(elevatorLevel);
            // ---------------------------------------
            List<Relation> rel = w.referrers(Relation.class).collect(Collectors.toList());
            if (!rel.isEmpty()) {
                if (wayWithRelations != null)
                    throw new AssertionError("Cannot fix duplicate Ways: More than one way is relation member.");
                wayWithRelations = w;
                relations = rel;
            }
            // Only one way will be kept - the one with lowest positive ID, if such exist
            // or one "at random" if no such exists. Rest of the ways will be deleted
            if (!w.isNew() && (idToKeep == 0 || w.getId() < idToKeep)) {
                idToKeep = w.getId();
                wayToKeep = w;
            }
        }

        Collection<Command> commands = new LinkedList<>();

        // Fix relations.
        if (wayWithRelations != null && relations != null && wayToKeep != wayWithRelations) {
            for (Relation rel : relations) {
                Relation newRel = new Relation(rel);
                for (int i = 0; i < newRel.getMembers().size(); ++i) {
                    RelationMember m = newRel.getMember(i);
                    if (wayWithRelations.equals(m.getMember())) {
                        newRel.setMember(i, new RelationMember(m.getRole(), wayToKeep));
                    }
                }
                commands.add(new ChangeCommand(rel, newRel));
            }
        }

        //Order the level ArrayList
        Collections.sort(elevatorLevels);


        //Add ArrayList elements to StringBuffer element
        for (String s : elevatorLevels) {
            if (elevatorLevels.indexOf(s) == (elevatorLevels.size() -1)) {
                sb.append(s);
            }
            else {
                sb.append(s);
                sb.append(";");
            }

        }

        //Convert StringBuffer to String
        newLevelsTag = sb.toString();

        // Set the level values for remaining elevator
        commands.add(new ChangePropertyCommand(wayToKeep, "level", newLevelsTag));

        // Delete all ways in the list
        // Note: nodes are not deleted, these can be detected and deleted at next pass
        wayz.remove(wayToKeep);
        commands.add(new DeleteCommand(wayz));
        return new SequenceCommand(tr("Delete duplicate elevators"), commands);
    }

    @Override
    public boolean isFixable(TestError testError) {
        if (!(testError.getTester() instanceof DuplicateElevator))
            return false;

        // Do not automatically fix same ways with different tags --> next line as comment since we also want to fix same position elevators
        //if (testError.getCode() != DUPLICATE_ELEVATOR) return false;

        // We fix it only if there is no more than one way that is relation member.
        Set<Way> wayz = testError.primitives(Way.class).collect(Collectors.toSet());
        if (wayz.size() < 2)
            return false;

        long waysWithRelations = wayz.stream()
                .filter(w -> w.referrers(Relation.class).anyMatch(x -> true))
                .count();
        return waysWithRelations <= 1;
    }
}
