//Java imports
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

//ELK imports
import com.google.common.collect.Sets;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;

//OWL API
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;

/**
 * Created by:      Lucas Beasley
 * Date:            10/30/17
 * Purpose:         Compares annotations from various tools against manually annotated papers in the CRAFT corpus,
 *                  then computes the average Jaccard values for each paper. Also calculates the counts of the total
 *                  annotations (splitting into exact matches, partial matches, and new annotations for a tool) and
 *                  the number of unique Gene Ontology annotations for the CRAFT and all tools.
 */
public class averageJaccard {
    private OWLOntology go_ontology;
    private OWLDataFactory factory;
    private OWLReasoner reasoner;
    private String go_prefix;

    /***
     * An Annotation contains each individual annotation in a paper for a tool.
     */
    public class Annotation{
        private String term = "";           //term in paper
        private String id = "";             //GO:ID for ontology term
        private String ref = "";            //ontology term
        private int startIndex = -1;        //term's starting index in paper
        private int endIndex = -1;          //term's ending index in paper
        private int level = 0;              //ID's level in ontology

        //getters/setters
        public String getTerm(){ return this.term; }
        public String getID(){ return this.id; }
        private String getRef(){ return this.ref; }
        private int getStartIndex(){ return this.startIndex; }
        private int getEndIndex(){ return this.endIndex; }
        private int getLevel(){ return this.level; }
        public void setTerm(String term){ this.term = term; }
        public void setID(String id){ this.id = id; }
        private void setRef(String ref){ this.ref = ref; }
        private void setStartIndex(int start){ this.startIndex = start; }
        private void setEndIndex(int end){ this.endIndex = end; }
        private void setLevel(int level){ this.level = level; }
    }

//    /***
//     * Annotation class for MetaMap annotations
//     */
//    public class mmAnnotation extends Annotation{
//        private String prefName = "";       //preferred name
//        private String cuid = "";           //CUID
//
//        //getters/setters
//        public String getPrefName(){ return this.prefName; }
//        public String getCuid(){ return this.cuid; }
//        private void setPrefName(String prefName){ this.prefName = prefName; }
//        private void setCuid(String cuid){ this.cuid = cuid; }
//    }
//
//    /***
//     * Annotation class for NCBO annotations
//     */
//    public class ncboAnnotation extends Annotation{
//        private String matchType = "";      //match type of annotation (pref or syn)
//
//        //getter/setter
//        public String getMatchType(){ return this.matchType; }
//        private void setMatchType(String matchType){ this.matchType = matchType; }
//    }

    /***
     * A PartialMatch contains the GO:IDs for a partial match (annotation with same indices, but different GO:ID), as
     */
    public class PartialMatch{
        private String craftID = "";            //GO:ID CRAFT returned
        private String toolID = "";             //GO:ID tool returned

        //constructor
        public PartialMatch(String cid, String tid){
            this.craftID = cid;
            this.toolID = tid;
        }

        //getters/setters
        public void setCraftID(String cid){ this.craftID = cid; }
        public void setToolID(String tid){ this.toolID = tid; }
        public String getCraftID(){ return craftID; }
        public String getToolID(){ return toolID; }
    }

    private class CountsAndPartials{
        private int exacts = 0;                                     //total number of exact matches
        private int partials = 0;                                   //total number of partial matches
        private int unique = 0;                                     //total number of unique GO:IDs
        private int newannotations = 0;                             //total number of new annotations
        private int falsenegatives = 0;                             //total number of false negatives
        private List<PartialMatch> matches = new ArrayList<>();     //list of partial matches

        //getters/setters
        private int getExacts(){ return exacts; }
        private int getPartials(){ return partials; }
        private int getUnique(){ return unique; }
        private int getNewAnnotations(){ return newannotations; }
        private List<PartialMatch> getMatches(){ return matches; }
        private int getFalseNegatives(){ return falsenegatives; }
        private void setExacts(int exacts){ this.exacts = exacts; }
        private void setMatches(List<PartialMatch> matches){ this.matches = matches; }
        private void setNewAnnotations(int newannotations){ this.newannotations = newannotations; }
        private void setUnique(int unique){ this.unique = unique; }
        private void setPartials(int partials){ this.partials = partials;}
        private void setFalseNegatives(int falseNegatives){ this.falsenegatives = falseNegatives; }
    }

    public static void main(String[] args) {
        averageJaccard avgj = new averageJaccard();
        //Directories for CRAFT annotations
        File craft_cc = new File("input/craftAnnotations/go_cc");
        File craft_bpmf = new File("input/craftAnnotations/go_bpmf");
        //Directory for NCBO Annotations
        File ncbo = new File("input/ncboAnnotations");
        //Directory for Textpresso Annotations
        File textpresso = new File("input/textpressoAnnotations");
        //Directory for MetaMap Annotations
        File metamap = new File("input/metamapAnnotations");
        //Directory for Scigraph Annotations
        File scigraph = new File("input/scigraphAnnotations");
        //GO Ontology file
        File ontology = new File("go-plus.owl");
        //GO BP ID file
        File bp_file = new File("input/idFiles/bp_classes.tsv");
        //GO CC ID file
        File cc_file = new File("input/idFiles/cc_classes.tsv");
        //GO MF ID file
        File mf_file = new File("input/idFiles/mf_classes.tsv");

        //Pull all of the GO:IDs into their separate branched lists
        List<String> bp_ids = avgj.pullIDs(bp_file);
        List<String> cc_ids = avgj.pullIDs(cc_file);
        List<String> mf_ids = avgj.pullIDs(mf_file);

        //Pull all annotations into maps
        Map<String, List<Annotation>> craft_annos = avgj.mergeMaps(avgj.pullCRAFTAnnos(craft_cc),
                                                    avgj.pullCRAFTAnnos(craft_bpmf));
        Map<String, List<Annotation>> ncbo_annos = avgj.pullAnnos(ncbo);
        Map<String, List<Annotation>> textpresso_annos = avgj.pullAnnos(textpresso);
        Map<String, List<Annotation>> metamap_annos = avgj.pullAnnos(metamap);
        Map<String, List<Annotation>> scigraph_annos = avgj.pullAnnos(scigraph);

        //Setup the ontology
        avgj.setupOntology(ontology);

        //Get the ontology level for all annotations
        craft_annos = avgj.calculateLongestPaths(craft_annos);
        ncbo_annos = avgj.calculateLongestPaths(ncbo_annos);
        textpresso_annos = avgj.calculateLongestPaths(textpresso_annos);
        metamap_annos = avgj.calculateLongestPaths(metamap_annos);
        scigraph_annos = avgj.calculateLongestPaths(scigraph_annos);

        //Get the level distribution for CRAFT/each tool.
        Map<Integer, Integer> craft_level_dist = avgj.calculateLevelDistribution(craft_annos);
        Map<Integer, Integer> ncbo_level_dist = avgj.calculateLevelDistribution(ncbo_annos);
        Map<Integer, Integer> textpresso_level_dist = avgj.calculateLevelDistribution(textpresso_annos);
        Map<Integer, Integer> metamap_level_dist = avgj.calculateLevelDistribution(metamap_annos);
        Map<Integer, Integer> scigraph_level_dist = avgj.calculateLevelDistribution(scigraph_annos);

        //Write level distributions to file
        File level_output = new File("output/level_distribution");
        avgj.writeOut(craft_level_dist, ncbo_level_dist, textpresso_level_dist, metamap_level_dist, scigraph_level_dist,
                level_output);

        //Compare CRAFT annotations to tools, get the match counts (total, partial, new), and list of partial matches
        Map<String, CountsAndPartials> ncbo_counts = avgj.compareAnnotations(craft_annos, ncbo_annos);
        Map<String, CountsAndPartials> textpresso_counts = avgj.compareAnnotations(craft_annos, textpresso_annos);
        Map<String, CountsAndPartials> metamap_counts = avgj.compareAnnotations(craft_annos, metamap_annos);
        Map<String, CountsAndPartials> scigraph_counts = avgj.compareAnnotations(craft_annos, scigraph_annos);

        //Retrieve the total counts (exact, partial, new annotations, unique GO:IDs) for each tool and CRAFT
        int[] craft_total = avgj.getCRAFTTotalCounts(craft_annos, bp_ids, cc_ids, mf_ids);
        CountsAndPartials ncbo_total = avgj.totalCounts(ncbo_counts);
        CountsAndPartials textpresso_total = avgj.totalCounts(textpresso_counts);
        CountsAndPartials metamap_total = avgj.totalCounts(metamap_counts);
        CountsAndPartials scigraph_total = avgj.totalCounts(scigraph_counts);
        avgj.countUniqueGOs(ncbo_total, ncbo_annos);
        avgj.countUniqueGOs(textpresso_total, textpresso_annos);
        avgj.countUniqueGOs(metamap_total, metamap_annos);
        avgj.countUniqueGOs(scigraph_total, scigraph_annos);

        //Write total counts to files
        File totals_output = new File("output/totals");
        avgj.writeOut(craft_total, ncbo_total, textpresso_total, metamap_total, scigraph_total, totals_output);

        //Calculate Jaccard values for each paper
        Map<String, double[]> ncbo_jaccards = avgj.calculateJaccards(ncbo_counts);
        Map<String, double[]> textpresso_jaccards = avgj.calculateJaccards(textpresso_counts);
        Map<String, double[]> metamap_jaccards = avgj.calculateJaccards(metamap_counts);
        Map<String, double[]> scigraph_jaccards = avgj.calculateJaccards(scigraph_counts);

        //Calculate mean of Jaccard values and their standard deviation for each paper
        Map<String, Double> ncbo_avg_jaccard = avgj.calculateMean(ncbo_jaccards);
        Map<String, Double> textpresso_avg_jaccard = avgj.calculateMean(textpresso_jaccards);
        Map<String, Double> metamap_avg_jaccard = avgj.calculateMean(metamap_jaccards);
        Map<String, Double> scigraph_avg_jaccard = avgj.calculateMean(scigraph_jaccards);

        //Write average Jaccards to files
        File ncbo_output = new File("output/ncbo_avg");
        File textpresso_output = new File("output/textpresso_avg");
        File metamap_output = new File("output/metamap_avg");
        File scigraph_output = new File("output/scigraph_avg");
        avgj.writeOut(ncbo_avg_jaccard, ncbo_output);
        avgj.writeOut(textpresso_avg_jaccard,textpresso_output);
        avgj.writeOut(metamap_avg_jaccard, metamap_output);
        avgj.writeOut(scigraph_avg_jaccard, scigraph_output);

        //Calculate average mean Jaccard value and average 2nd standard error of the mean for each tool
        double[] ncbo_avg_mean_and_dev = avgj.calculateAvgAndDevForTool(ncbo_avg_jaccard);
        double[] textpresso_avg_mean_and_dev = avgj.calculateAvgAndDevForTool(textpresso_avg_jaccard);
        double[] metamap_avg_mean_and_dev = avgj.calculateAvgAndDevForTool(metamap_avg_jaccard);
        double[] scigraph_avg_mean_and_dev = avgj.calculateAvgAndDevForTool(scigraph_avg_jaccard);

        //Write overall average Jaccard and 2nd standard error of the mean for each tool to a file
        File tools_output = new File("output/tool_avgs");
        avgj.writeOut(ncbo_avg_mean_and_dev, textpresso_avg_mean_and_dev, metamap_avg_mean_and_dev,
                scigraph_avg_mean_and_dev, tools_output);


        boolean bool = true;
    }

    /***
     * pullCRAFTAnnos retrieves the annotations from the annotation files in the passed directory.
     * @param annoDirectory - directory of CRAFT annotations
     * @return map with the filename as a key and a list of its respective annotations as a value
     */
    private Map<String, List<Annotation>> pullCRAFTAnnos(File annoDirectory){
        Map<String, List<Annotation>> craftAnnos = new HashMap<>();
        List<Annotation> annotations;
        Annotation tempAnno;
        Scanner scan;
        String line, filename, class_id, start, end, text, go_id, ref, prevline;
        int symbolIndex, nextSymbolIndex, starter, ender;

        for (File file: annoDirectory.listFiles()){
            try{
                scan = new Scanner(file);
                annotations = new ArrayList<>();
                //retrieve file name from second line
                scan.nextLine();
                line = scan.nextLine();
                filename = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\""));
                filename = filename.substring(0, filename.length()-4);

                while(scan.hasNextLine()){
                    line = scan.nextLine();

                    //check if annotation
                    if(line.contains("<annotation>")){
                        tempAnno = new Annotation();
                        //pull GO:ID
                        line = scan.nextLine();
                        class_id = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\""));
                        tempAnno.setID(class_id);
                        scan.nextLine();

                        //pull start and end indexes
                        //*can have multiple start/end indexes if text spans out*
                        line = scan.nextLine();

                        symbolIndex = line.indexOf("\"");
                        nextSymbolIndex = line.indexOf("\"", symbolIndex+1);
                        start = line.substring(symbolIndex+1, nextSymbolIndex);
                        starter = Integer.parseInt(start);
                        tempAnno.setStartIndex(starter);

                        prevline = line;
                        line = scan.nextLine();

                        //check if contains multiple start/end indexes
                        while(line.contains("<span start")){
                            prevline = line;
                            line = scan.nextLine();
                        }

                        //cycle through double quotes to get to the final end index
                        symbolIndex = prevline.indexOf("\"");
                        nextSymbolIndex = prevline.indexOf("\"", symbolIndex+1);
                        symbolIndex = prevline.indexOf("\"", nextSymbolIndex+1);
                        nextSymbolIndex = prevline.indexOf("\"", symbolIndex+1);
                        end = prevline.substring(symbolIndex+1, nextSymbolIndex);
                        ender = Integer.parseInt(end);
                        tempAnno.setEndIndex(ender);


                        //pull term
                        symbolIndex = line.indexOf(">");
                        nextSymbolIndex = line.lastIndexOf("<");
                        text = line.substring(symbolIndex+1, nextSymbolIndex);
                        tempAnno.setTerm(text);

                        scan.nextLine();
                        annotations.add(tempAnno);
                    }
                    //check if GO:ID reference
                    else if(line.contains("<classMention ")){
                        //pull mention ID, GO:ID referred by mention ID, and reference term
                        class_id = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\""));
                        line = scan.nextLine();
                        go_id = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\""));
                        ref = line.substring(line.indexOf(">")+1, line.lastIndexOf("<"));
                        //if annotation uses mention ID, replace with GO:ID and assign reference term
                        for (Annotation a: annotations){
                            if(a.getID().equals(class_id)){
                                a.setID(go_id);
                                a.setRef(ref);
                            }
                        }
                    }
                }
                annotations.sort(Comparator.comparing(Annotation::getEndIndex));
                craftAnnos.put(filename, annotations);
            }catch (FileNotFoundException ex){
                System.out.println("Error: File not found. File: " + file);
            }
        }
        return craftAnnos;
    }

    /***
     * mergeMaps merges the lists of two maps together (used for CRAFT annotations)
     * @param map1 - map of CRAFT go_cc annotations
     * @param map2 - map of CRAFT go_bpmf annotations
     * @return merged mapping of all CRAFT annotations
     */
    private Map<String, List<Annotation>> mergeMaps(Map<String, List<Annotation>> map1,
                                                    Map<String, List<Annotation>> map2){
        List<Annotation> list1, list2;

        //check 2nd map
        for(String key: map2.keySet()){
            //if map1 has the same file then merge lists, sort, and replace list in map1
            if(map1.containsKey(key)){
                list1 = map1.get(key);
                list2 = map2.get(key);
                list1.addAll(list2);
                list1.sort(Comparator.comparing(Annotation::getEndIndex));
                map1.replace(key, list1);
            }
            //if map1 does not have the same key, then add key/value pair to map1
            else{
                list2 = map2.get(key);
                map1.put(key, list2);
            }
        }
        return map1;
    }

    /***
     * pullAnnos pulls annotations from a tab-separated annotation file.
     * @param annoDirectory - directory containing multiple annotation files (.tsv)
     * @return map of annotations per file for a tool
     */
    private Map<String, List<Annotation>> pullAnnos(File annoDirectory){
        Map<String, List<Annotation>> annoMap = new HashMap<>();
        List<Annotation> annotations;
        Scanner scan;
        Annotation a;
        String filename, startIndex, endIndex, line;
        String[] values, fix;
        int start, end;

        for(File f: annoDirectory.listFiles()){
            filename = f.getName();
            filename = filename.substring(0, filename.length()-4);
            try{
                scan = new Scanner(f);
                annotations = new ArrayList<>();
                //skip headers
                scan.nextLine();
                //pull each annotation and set the values
                while(scan.hasNextLine()){
                    line = scan.nextLine();
                    values = line.split("\t");
                    a = new Annotation();
                    //textpresso annotations have some blank values, fix if necessary
                    if(values.length == 4){
                        if(!(Integer.parseInt(values[0]) >= 0)){
                            values[0] = "-1";
                        }
                        else if(!(Integer.parseInt(values[1]) >= 0)){
                            values[1] = "-1";
                        }
                        else if(!(values[2].contains("GO:"))){
                            values[2] = "N/A";
                        }
                        else{
                            fix = Arrays.copyOf(values, 5);
                            fix[4] = "N/A";
                            values = fix;
                        }
                    }
                    startIndex = values[0];
                    start = Integer.parseInt(startIndex);
                    a.setStartIndex(start);
                    endIndex = values[1];
                    end = Integer.parseInt(endIndex);
                    a.setEndIndex(end);
                    a.setID(values[2]);
                    a.setTerm(values[3]);
                    a.setRef(values[4]);
                    annotations.add(a);
                }
                //add each file and annotations pair to map
                annoMap.put(filename, annotations);
            }catch(FileNotFoundException ex){
                System.out.println("Error: File " + filename + " not found.");
            }
        }
        return annoMap;
    }

    /***
     * pullIDs pulls the GO:IDs from a file (BP, CC, MF)
     * @param idFile - file containing the IDs within a branch of the GO
     * @return list containing the IDs within a branch of the GO
     */
    private List<String> pullIDs(File idFile){
        List<String> ids = new ArrayList<>();
        String line, id;
        String[] goidMappings;
        try {
            Scanner scan = new Scanner(idFile);
            while(scan.hasNextLine()){
                line = scan.nextLine();
                goidMappings = line.split("\t");
                id = goidMappings[0].replace("_", ":");
                if(!ids.contains(id)){
                    ids.add(id);
                }
            }
        }catch (FileNotFoundException ex){
            System.out.println("Error: File not found.");
        }

        return ids;
    }

    /***
     * annotationComparison counts the number of total and partial annotation matches, as well as newly created
     * annotations, for a tool vs. the CRAFT corpus. It also creates a list of the partial matches (GO:IDs that were
     * matched by CRAFT and tool, and indices of the tagged term).
     * Annotation matches are based upon GO:ID match and beginning and ending indicies for a term.
     * @param craft - map of CRAFT annotations to each file
     * @param tool - map of tool annotations to each file
     * @return map that contains the total number of exact and partial matches, total number of new annotations created
     * by the tool, and a list of the partial matches (GO:ID from CRAFT and GO:ID from tool). This maps to each filename.
     */
    private Map<String, CountsAndPartials> compareAnnotations(Map<String, List<Annotation>> craft,
                                                                       Map<String,List<Annotation>> tool){
        Map<String, CountsAndPartials> countsperpaper = new HashMap<>();
        PartialMatch pm;
        List<Annotation> craftannos, toolannos;
        /*
        Counts for total number of exact matches for a tool, total number of partial matches for a tool,
        total number of newly created annotations for a tool, total number of annotations for the CRAFT Corpus.
         */
        boolean flag;
        //pull craft keys and lists
        for(String key: craft.keySet()){
            craftannos = craft.get(key);
            CountsAndPartials counts = new CountsAndPartials();
            List<PartialMatch> partialMatchList = new ArrayList<>();
            //check if tool contains key
            if(tool.containsKey(key)){
                //pull list of annos and check against craft
                toolannos = tool.get(key);
                for(Annotation a: craftannos){
                    flag = false;
                    for(Annotation b: toolannos){
                        //if both contain same indices
                        if(a.getStartIndex() == b.getStartIndex() && a.getEndIndex() == b.getEndIndex()){
                            flag = true;
                            //same GO:ID?
                            if(a.getID().equals(b.getID())){
                                counts.setExacts(counts.getExacts()+1); //both same, add to total match count
                            }
                            else{
                                //tagged term at indices, but incorrect GO:ID
                                pm = new PartialMatch(a.getID(), b.getID());
                                counts.setPartials(counts.getPartials()+1); //up the count for partial match
                                partialMatchList.add(pm); //add new partial match
                            }
                        }
                    }
                    //if CRAFT has annotation at indices but tool does not, increase false negatives
                    if(!flag){
                        counts.setFalseNegatives(counts.getFalseNegatives()+1);
                    }
                }
                //total new annotations that the tool created
                counts.setNewAnnotations(toolannos.size() - (counts.getExacts() + counts.getPartials()));
                counts.setMatches(partialMatchList);
                //add counts/partial matches to map
                countsperpaper.put(key, counts);
            }
        }
        return countsperpaper;
    }

    /***
     * countUniqueGOs gets a total count of the unique GO:IDs for all annotations from a tool
     * @param counts - CountsAndPartials object that stores the count for the unique GOs
     * @param tool - map of annotations
     */
    private void countUniqueGOs(CountsAndPartials counts, Map<String, List<Annotation>> tool){
        List<String> goids = new ArrayList<>();
        for(String key: tool.keySet()){
            for(Annotation a: tool.get(key)){
                if(!goids.contains(a.getID())){
                    goids.add(a.getID());
                }
            }
        }
        counts.setUnique(goids.size());
    }

    /***
     * totalCounts gets the total of each count within the CountsAndPartials for a tool.
     * @param tool - map of counts and partials for each file of a particular tool
     * @return CountsAndPartials object that contains the total number of exact, partial, and new annotations for a tool.
     */
    private CountsAndPartials totalCounts(Map<String, CountsAndPartials> tool){
        CountsAndPartials total = new CountsAndPartials(), temp;
        //total up the counts from each file
        for(String key : tool.keySet()){
            temp = tool.get(key);
            total.setExacts(total.getExacts() + temp.getExacts());
            total.setPartials(total.getPartials() + temp.getPartials());
            total.setNewAnnotations(total.getNewAnnotations() + temp.getNewAnnotations());
            total.setFalseNegatives(total.getFalseNegatives() + temp.getFalseNegatives());
        }
        return total;
    }

    /***
     * craftTotalCounts gets the total number of annotations and unique annotations in the corpus, and average
     * non-unique annotations per file.
     * @param craft_annos - map of annotations for each file in the corpus
     * @return integer array containing the counts for the CRAFT
     */
    private int[] getCRAFTTotalCounts(Map<String, List<Annotation>> craft_annos, List<String> bp_ids,
                                         List<String> cc_ids, List<String> mf_ids){
        /* craft_total: [0] total annotations in corpus, [1] total unique annotations in corpus,
        [2] avg nonunique annotations per paper, [3] total bp annotations, [4] total unique bp IDs, 
        [5] total cc annotations, [6] total unique cc IDs, [7] total mf annotations, [8] total unique mf IDs,
        [9] total independent_continuants, [10] total annotations with ids not found, [11] total ids not found
         */
        String id;
        int[] craft_total = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Map<String, Integer> craft_nonunique = new HashMap<>();
        List<String> craft_goids = new ArrayList<>();
        List<String> seen_bps = new ArrayList<>();
        List<String> seen_ccs = new ArrayList<>();
        List<String> seen_mfs = new ArrayList<>();
        List<String> not_found = new ArrayList<>();
        for(String key: craft_annos.keySet()){
            int paper_nonunique, paper_total = 0;
            List<String> paper_goids = new ArrayList<>();
            for(Annotation a: craft_annos.get(key)){
                //increment total count of annotations for entire corpus
                craft_total[0]++;
                //increment total count of annotations for the current paper
                paper_total++;
                //keep track of the unique GO:IDs for the entire corpus
                if(!craft_goids.contains(a.getID())){
                    craft_goids.add(a.getID());
                }
                //keep track of the unique GO:IDs in the current paper
                if(!paper_goids.contains(a.getID())){
                    paper_goids.add(a.getID());
                }

                id = a.getID();
                if(id.equals("independent_continuant")){
                    craft_total[9]++;
                }
                //check if a BP annotation
                else if(bp_ids.contains(id)){
                    //increase total bp annotation count
                    craft_total[3]++;
                    if(!seen_bps.contains(id)){
                        //increase unique bp annotation count and add to "seen" list
                        craft_total[4]++;
                        seen_bps.add(id);
                    }
                }
                //check if a CC annotation
                else if(cc_ids.contains(id)){
                    //increase total cc annotation count
                    craft_total[5]++;
                    if(!seen_ccs.contains(id)){
                        //increase unique cc annotation count and add to "seen" list
                        craft_total[6]++;
                        seen_ccs.add(id);
                    }
                }
                //check if a MF annotation
                else if(mf_ids.contains(id)){
                    //increase total mf annotation count
                    craft_total[7]++;
                    if(!seen_mfs.contains(id)){
                        //increase unique mf annotation count and add to "seen" list
                        craft_total[8]++;
                        seen_mfs.add(id);
                    }
                }
                else{
                    System.out.println("Error: ID " + id + " not found.");
                    if(!not_found.contains(id)){
                        not_found.add(id);
                        craft_total[11]++;
                    }
                    craft_total[10]++;
                }
            }
            
            //calculate the total number of nonunique annotations within a paper
            paper_nonunique = paper_total - paper_goids.size();
            craft_nonunique.put(key, paper_nonunique);
        }
        //calculate the average number of nonunique annotations in a paper
        int total_nonunique = 0, avg_nonunique;
        for(String key : craft_nonunique.keySet()){
            total_nonunique += craft_nonunique.get(key);
        }
        avg_nonunique = total_nonunique / craft_nonunique.size();
        //total number of unique annotations in corpus
        craft_total[1] = craft_goids.size();
        //average number of nonunique annotations in a paper
        craft_total[2] = avg_nonunique;

        return craft_total;
    }

    /***
     * calculateAverageJaccards calculates the intersection and union of the superclasses for CRAFT and the tool GO:IDs.
     * It then gets the Jaccard values and averages them.
     * @param toolcounts - map containing the filename and the list of partial matches (GO:IDs)
     * @return map containing the filename and the average Jaccard value
     */
    private Map<String, double[]> calculateJaccards(Map<String, CountsAndPartials> toolcounts){
        Map<String, double[]> alljaccards = new HashMap<>();
        CountsAndPartials counts;
        double[] jaccards;
        List<PartialMatch> partialMatches;
        Set<String> all, inbetween;
        String craftID, toolID;
        int arrIndex, total_exacts;

        //go through files and retrieve partial matches
        for(String key: toolcounts.keySet()){
            counts = toolcounts.get(key);
            partialMatches = counts.getMatches();
            total_exacts = counts.getExacts();
            jaccards = new double[partialMatches.size() + total_exacts];
            arrIndex = 0;
            all = new HashSet<>();
            inbetween = new HashSet<>();
            if(partialMatches.size() != 0 || total_exacts != 0) {
                if(partialMatches.size() != 0) {
                    //calculate Jaccard similarities for CRAFT vs. tool
                    for (PartialMatch pm : partialMatches) {
                        craftID = pm.getCraftID();
                        craftID = craftID.replace(":", "_");
                        toolID = pm.getToolID();
                        toolID = toolID.replace(":", "_");

                        //get superclasses for craftID
                        Set<OWLClass> craftsupersOWL = getSupers(craftID);
                        Set<String> craftsupers = new HashSet<>();
                        for (OWLClass owlClass : craftsupersOWL) {
                            craftsupers.add(owlClass.getIRI().getShortForm());
                        }

                        //get superclasses for toolID
                        Set<OWLClass> toolsupersOWL = getSupers(toolID);
                        Set<String> toolsupers = new HashSet<>();
                        for (OWLClass owlClass : toolsupersOWL) {
                            toolsupers.add(owlClass.getIRI().getShortForm());
                        }

                        //retrieve the intersection and union of the sets of superclasses
                        Set<String> intersection = Sets.intersection(craftsupers, toolsupers);
                        Set<String> union = Sets.union(craftsupers, toolsupers);

                        //remove root from sets and add originating IDs into union set
                        all.addAll(union);
                        all.remove("Thing");
                        all.add(craftID);
                        all.add(toolID);
                        inbetween.addAll(intersection);
                        inbetween.remove("Thing");
//                        System.out.println("Union: " + all);
//                        System.out.println("Intersection: " + inbetween);
                        //calculate jaccard values
                        jaccards[arrIndex] = (double) (inbetween.size()) / (double) (all.size());

                        //increase the index for jaccards
                        arrIndex++;
                    }
                }
                //add in exact matches; assign 1.0 jaccard values
                for(int i = 0; i < total_exacts; i++){
                    jaccards[arrIndex] = 1.0;
                    arrIndex++;
                }
                alljaccards.put(key, jaccards);
            }
        }
        return alljaccards;
    }

    /***
     * getSupers retrieves the superclasses of a GO:ID from the Gene Ontology
     * @param goID - ID that the superclasses will be retrieved for
     * @return set of superclasses
     */
    private Set<OWLClass> getSupers(String goID){
        OWLClass owlClass = this.factory.getOWLClass(IRI.create(this.go_prefix + goID));
        NodeSet<OWLClass> superClasses = this.reasoner.getSuperClasses(owlClass, false);
        return superClasses.getFlattened();
    }

    private Map<String, List<Annotation>> calculateLongestPaths(Map<String, List<Annotation>> annos){
        String id;
        //get annotations per file
        for(String key : annos.keySet()){
            for(Annotation a : annos.get(key)){
                //get the level of the annotation
                id = a.getID().replace(":", "_");
                a.setLevel(getLongestPathToID(id));
            }
        }
        return annos;
    }

    public int getLongestPathToID(String goID){
        int maxLevel = 0, level;
        OWLClass owlClass = this.factory.getOWLClass(IRI.create(this.go_prefix + goID));
        //get only the direct super classes for the GO:ID
        NodeSet<OWLClass> supers = this.reasoner.getSuperClasses(owlClass, true);
        for(Node<OWLClass> parent : supers){
            if (parent.isTopNode()){
                break;
            }
            level = getLongestPathToID(parent.getRepresentativeElement().getIRI().getShortForm());
            if (level > maxLevel){
                maxLevel = level;
            }
        }
        return maxLevel + 1;
    }

    private Map<Integer, Integer> calculateLevelDistribution(Map<String, List<Annotation>> annos){
        Map<Integer, Integer> level_dist = new HashMap<>();
        int level, curr_count;
        //pull annotations from file
        for(String key : annos.keySet()){
            for(Annotation a : annos.get(key)){
                //get anno level
                level = a.getLevel();
                //add level to map if doesn't exist
                if(!level_dist.containsKey(level)){
                    level_dist.put(level, 1);
                }
                //increase counter if map contains level
                else{
                    curr_count = level_dist.get(level);
                    level_dist.replace(level, curr_count+1);
                }
            }
        }
        return level_dist;
    }

    /***
     * setupOntology sets up the GO ontology for use with the OWL API
     * @param ontology - OWL file that contains the ontology
     */
    private void setupOntology(File ontology){
        try{
            //setup ontology
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            this.factory = manager.getOWLDataFactory();
            this.go_ontology = manager.loadOntologyFromOntologyDocument(ontology);
            this.go_prefix = "http://purl.obolibrary.org/obo/";

            //setup ELK reasoner
            OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
            this.reasoner = reasonerFactory.createReasoner(this.go_ontology);
            this.reasoner.precomputeInferences();
            LogManager.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);
        }catch(OWLOntologyCreationException ex){
            System.out.println("Error: Cannot create ontology from " + ontology);
        }
    }

    /***
     * calculateMeanAndDeviation takes the Jaccard values for each paper and calculates the average
     * Jaccard value for each paper and the 2nd standard error of the mean.
     * @param tool_jaccards - map containing the Jaccard values for each file
     * @return map containing the mean Jaccard value and the 2nd standard error of the mean
     */
    private Map<String, Double> calculateMean(Map<String, double[]> tool_jaccards){
        Map<String, Double> toolmeans = new HashMap<>();
        double[] jaccards;
        double sum, mean;

        for(String key: tool_jaccards.keySet()){
            jaccards = tool_jaccards.get(key);
            sum = 0.0;

            //calculate mean of jaccards
            for (double val: jaccards) {
                sum += val;
            }
            mean = sum/jaccards.length;
            //if the average is 0, then GO:ID has probably been removed/updated; flag value with -1
            if(mean == 0.0) {
                mean = -1.0;
            }

            //round mean to two decimal places
            mean = Math.round(mean*100.0);
            mean = mean/100.0;

            toolmeans.put(key, mean);
        }
        return toolmeans;
    }

    /***
     * calculateAveragesForTool calculates the average mean Jaccard value and average deviation value for a tool.
     * @param tool_means- map from one tool containing all of the average Jaccard values for the files and all
     *                          of the deviations of the average Jaccard values
     * @return array containing the average mean Jaccard value and the average deviation for a tool
     */
    private double[] calculateAvgAndDevForTool(Map<String, Double> tool_means){
        double[] avgdevtoolvalues = new double[2];
        double mean = 0.0, meancounter = 0.0, temp = 0.0, stddev, twostandard;

        for(String key: tool_means.keySet()){
            Double val = tool_means.get(key);

            //if vals[x] is not a flag value (-1), count into mean
            if(!(val < 0.0)){
                mean += val;
                meancounter++;
            }
        }

        //get average mean
        mean = mean/meancounter;

        //calculate standard deviation
        for(String key: tool_means.keySet()){
            Double val = tool_means.get(key);
            temp += (val - mean)*(val - mean);
        }

        temp = temp/(tool_means.size()-1);
        stddev = Math.sqrt(temp);

        //calculate 2 standard errors of mean: 2*(std(jaccards)/sqrt(jaccards.length))
        twostandard = (2*(stddev/(Math.sqrt(tool_means.size()))));

        //round each value to two decimal places
        mean = Math.round(mean*100.0);
        mean = mean/100.0;
        twostandard = Math.round(twostandard*100.0);
        twostandard = twostandard/100.0;

        avgdevtoolvalues[0] = mean;
        avgdevtoolvalues[1] = twostandard;

        return avgdevtoolvalues;
    }

    /***
     * writeOut writes the average Jaccard value and the 2nd standard error of the mean for each paper to a new
     * tab-separated file.
     * @param toolavgjaccard - map containing the average Jaccard value and the 2nd standard error of the mean for
     *                       each file
     * @param filename - output file name
     */
    private void writeOut(Map<String, Double> toolavgjaccard, File filename){
        try(PrintWriter writer = new PrintWriter(filename)){
            writer.println("Filename\tAverageJaccard");
            for(String key: toolavgjaccard.keySet()){
                Double value = toolavgjaccard.get(key);
                //only write out to file if there are no flagged values (-1)
                if(value != -1.0){
                    writer.println(key + "\t" + value);
                }
            }
        }catch(FileNotFoundException ex){
            System.out.println("Error: Could not write to file " + filename);
        }
    }

    /***
     * writeOut writes the total number of exact, partial, new annotations, and unique GO:IDs for each tool to a single
     * tab-separated file.
     * @param ncbo - CountsAndPartials for NCBO
     * @param textpresso - CountsAndPartials for Textpresso
     * @param mm - CountsAndPartials for MetaMap
     * @param scigraph - CountsAndPartials for Scigraph
     * @param filename - output file name
     */
    private void writeOut(int[] craft, CountsAndPartials ncbo, CountsAndPartials textpresso, CountsAndPartials mm,
                          CountsAndPartials scigraph, File filename){
        try(PrintWriter writer = new PrintWriter(filename)){
            writer.println("CRAFT");
            writer.println("--------------------------");
            writer.println("Total: " + craft[0]);
            writer.println("Unique GO:IDs: " + craft[1]);
            writer.println("Average Non-Unique Annotations per paper: " + craft[2] + "\n");
            writer.println("GO Distribution in CRAFT");
            writer.println("--------------------------");
            writer.println("BP: Total: " + craft[3] + "\tUnique: " + craft[4]);
            writer.println("CC: Total: " + craft[5] + "\tUnique: " + craft[6]);
            writer.println("MF: Total: " + craft[7] + "\tUnique: " + craft[8] + "\n");
            writer.println("Total independent_continuants: " + craft[9]);
            writer.println("Total annotations with missing IDs (not counting independent_continuants): " + craft[10]);
            writer.println("Total IDs not found: " + craft[11] + "\n\n");
            writer.println("Tool\tExacts\tPartials\tNew\tUniqueGOs\tFalseNegatives\n");
            writer.println("NCBO\t" + ncbo.getExacts() + "\t" + ncbo.getPartials() + "\t" + ncbo.getNewAnnotations()
                    + "\t" + ncbo.getUnique() + "\t" + ncbo.getFalseNegatives());
            writer.println("Textpresso\t" + textpresso.getExacts() + "\t" + textpresso.getPartials()
                    + "\t" + textpresso.getNewAnnotations() + "\t" + textpresso.getUnique() + "\t"
                    + textpresso.getFalseNegatives());
            writer.println("MetaMap\t" + mm.getExacts() + "\t" + mm.getPartials() + "\t" + mm.getNewAnnotations()
                    + "\t" + mm.getUnique() + "\t" + mm.getFalseNegatives());
            writer.println("Scigraph\t" + scigraph.getExacts() + "\t" + scigraph.getPartials() + "\t"
                    + scigraph.getNewAnnotations() + "\t" + scigraph.getUnique() + "\t" + scigraph.getFalseNegatives());
        }catch(FileNotFoundException ex){
            System.out.println("Error: Could not write to file " + filename);
        }
    }

    /***
     * writeOut writes out the average mean and deviation for each tool to a file.
     * @param ncbovalues - average mean and deviation for NCBO
     * @param textpressovalues - average mean and deviation for Textpresso
     * @param metamapvalues - average mean and deviation for MetaMap
     * @param scigraphvalues - average mean and deviation for Scigraph
     * @param filename - output file name
     */
    private void writeOut(double[] ncbovalues, double[] textpressovalues, double[] metamapvalues,
                          double[] scigraphvalues, File filename){
        try(PrintWriter writer = new PrintWriter(filename)){
            writer.println("Tool\tAverageJaccard\tAverageDeviation");
            writer.println("NCBO\t" + ncbovalues[0] + "\t" + ncbovalues[1]);
            writer.println("Textpresso\t" + textpressovalues[0] + "\t" + textpressovalues[1]);
            writer.println("MetaMap\t" + metamapvalues[0] + "\t" + metamapvalues[1]);
            writer.println("Scigraph\t" + scigraphvalues[0] + "\t" + scigraphvalues[1]);
        }catch(FileNotFoundException ex){
            System.out.println("Error: Could not write to file " + filename);
        }
    }

    private void writeOut(Map<Integer, Integer> craftvalues, Map<Integer, Integer> ncbovalues,
                          Map<Integer, Integer> textpressovalues, Map<Integer, Integer> metamapvalues,
                          Map<Integer, Integer> scigraphvalues, File filename){
        try(PrintWriter writer = new PrintWriter(filename)){
            writer.println("CRAFT Distribution");
            writer.println("--------------------------");
            for(Integer key : craftvalues.keySet()){
                writer.println("Level " + key.toString() + ": " + craftvalues.get(key).toString());
            }
            writer.println("\n");
            writer.println("Textpresso Distribution");
            writer.println("--------------------------");
            for(Integer key : textpressovalues.keySet()){
                writer.println("Level " + key.toString() + ": " + textpressovalues.get(key).toString());
            }
            writer.println("\n");
            writer.println("MetaMap Distribution");
            writer.println("--------------------------");
            for(Integer key : metamapvalues.keySet()){
                writer.println("Level " + key.toString() + ": " + metamapvalues.get(key).toString());
            }
            writer.println("\n");
            writer.println("NCBO Distribution");
            writer.println("--------------------------");
            for(Integer key : ncbovalues.keySet()){
                writer.println("Level " + key.toString() + ": " + ncbovalues.get(key).toString());
            }
            writer.println("\n");
            writer.println("Scigraph Distribution");
            writer.println("--------------------------");
            for(Integer key : scigraphvalues.keySet()){
                writer.println("Level " + key.toString() + ": " + scigraphvalues.get(key).toString());
            }
        }catch(FileNotFoundException ex){
            System.out.println("Error: Could not write to file " + filename);
        }
    }
}