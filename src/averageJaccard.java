//Java imports
import java.io.File;
import java.io.FileNotFoundException;
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
 * Lucas Beasley
 * 10/30/17
 * Compares annotations from various tools against manually annotated papers in the CRAFT corpus, then computes the
 * average Jaccard values for each paper.
 */
public class averageJaccard {
    private OWLOntology go_ontology;
    private OWLDataFactory factory;
    private OWLReasoner reasoner;
    private String go_prefix;

    public class Annotation{
        private String term = "";           //term in paper
        private String id = "";             //GO:ID for ontology term
        private String ref = "";            //ontology term
        private int startIndex = -1;        //term's starting index in paper
        private int endIndex = -1;          //term's ending index in paper

        //getters/setters
        public void setTerm(String term){ this.term = term; }
        public void setID(String id){ this.id = id; }
        private void setRef(String ref){ this.ref = ref; }
        private void setStartIndex(int start){ this.startIndex = start; }
        private void setEndIndex(int end){ this.endIndex = end; }
        public String getTerm(){ return this.term; }
        public String getID(){ return this.id; }
        private String getRef(){ return this.ref; }
        private int getStartIndex(){ return this.startIndex; }
        private int getEndIndex(){ return this.endIndex; }
    }

    public class PartialMatch{
        private String craftID = "";            //GO:ID CRAFT returned
        private String toolID = "";             //GO:ID tool returned
        private int startIndex = 0;             //starting index for tagged term
        private int endIndex = 0;               //ending index for tagged term

        //constructor
        public PartialMatch(String cid, String tid, int start, int end){
            this.craftID = cid;
            this.toolID = tid;
            this.startIndex = start;
            this.endIndex = end;
        }

        //getters/setters
        public void setCraftID(String cid){ this.craftID = cid; }
        public void setToolID(String tid){ this.toolID = tid; }
        public String getCraftID(){ return craftID; }
        public String getToolID(){ return toolID; }
    }

    public class CountsAndPartials{
        private int exacts = 0;                                     //total number of exact matches
        private int partials = 0;                                   //total number of partial matches
        private int newannotations = 0;                             //total number of new annotations
        private List<PartialMatch> matches = new ArrayList<>();     //list of partial matches

        //getters/setters
        public void setExacts(int exacts){ this.exacts = exacts; }
        public void setMatches(List<PartialMatch> matches){ this.matches = matches; }
        public void setNewAnnotations(int newannotations){ this.newannotations = newannotations; }
        public void setPartials(int partials){ this.partials = partials;}
        public int getExacts(){ return exacts; }
        public int getPartials(){ return partials; }
        public int getNewAnnotations(){ return newannotations; }
        public List<PartialMatch> getMatches(){ return matches; }
    }

    public static void main(String[] args) {
        averageJaccard avgj = new averageJaccard();
        //Directories for CRAFT annotations
        File craft_cc = new File("craftAnnotations/go_cc");
        File craft_bpmf = new File("craftAnnotations/go_bpmf");
        //Directory for NCBO Annotations
        File ncbo = new File("ncboAnnotations");
        //Directory for Textpresso Annotations
        File textpresso = new File("textpressoAnnotations");
        //GO Ontology file
        File ontology = new File("go.owl");

        //Pull all annotations into maps
        Map<String, List<Annotation>> craft_annos = avgj.mapMerge(avgj.pullCRAFTAnnos(craft_cc), avgj.pullCRAFTAnnos(craft_bpmf));
        Map<String, List<Annotation>> ncbo_annos = avgj.pullAnnos(ncbo);
        Map<String, List<Annotation>> textpresso_annos = avgj.pullAnnos(textpresso);

        //Compare CRAFT annotations to tools, get the match counts (total, partial, new), and list of partial matches
        Map<String, CountsAndPartials> ncbo_counts = avgj.compareAnnotations(craft_annos, ncbo_annos);
        Map<String, CountsAndPartials> textpresso_counts = avgj.compareAnnotations(craft_annos, textpresso_annos);

        //Setup the ontology
        avgj.setupOntology(ontology);

        //Calculate mean Jaccard values for each paper
        Map<String, Double> ncbo_avg_jaccard = avgj.calculateAverageJaccards(ncbo_counts);
        Map<String, Double> textpresso_avg_jaccard = avgj.calculateAverageJaccards(textpresso_counts);
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
                Collections.sort(annotations, Comparator.comparing(Annotation::getEndIndex));
                craftAnnos.put(filename, annotations);
            }catch (FileNotFoundException ex){
                System.out.println("Error: File not found. File: " + file);
            }
        }
        return craftAnnos;
    }

    /***
     * mapMerge merges the lists of two maps together (used for CRAFT annotations)
     * @param map1 - map of CRAFT go_cc annotations
     * @param map2 - map of CRAFT go_bpmf annotations
     * @return merged mapping of all CRAFT annotations
     */
    private Map<String, List<Annotation>> mapMerge(Map<String, List<Annotation>> map1,
                                                          Map<String, List<Annotation>> map2){
        List<Annotation> list1, list2;

        //check 2nd map
        for(String key: map2.keySet()){
            //if map1 has the same key then merge lists, sort, and replace list in map1
            if(map1.containsKey(key)){
                list1 = map1.get(key);
                list2 = map2.get(key);
                list1.addAll(list2);
                Collections.sort(list1, Comparator.comparing(Annotation::getEndIndex));
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
        CountsAndPartials counts;
        PartialMatch pm;
        List<PartialMatch> partialMatchList;
        List<Annotation> craftannos, toolannos;

        //pull craft keys and lists
        for(String key: craft.keySet()){
            craftannos = craft.get(key);
            counts = new CountsAndPartials();
            partialMatchList = new ArrayList<>();
            //check if tool contains key
            if(tool.containsKey(key)){
                //pull list of annos and check against craft
                toolannos = tool.get(key);
                for(Annotation a: craftannos){
                    for(Annotation b: toolannos){
                        //if both contain same indices
                        if(a.getStartIndex() == b.getStartIndex() && a.getEndIndex() == b.getEndIndex()){
                            //same GO:ID?
                            if(a.getID().equals(b.getID())){
                                counts.setExacts(counts.getExacts()+1); //both same, add to total match count
                            }
                            else{
                                //tagged term at indices, but incorrect GO:ID
                                pm = new PartialMatch(a.getID(), b.getID(), a.getStartIndex(), a.getEndIndex());
                                counts.setPartials(counts.getPartials()+1); //up the count for partial match
                                partialMatchList.add(pm); //add new partial match
                            }
                        }
                    }
                }
                //total new annotations that the tool created
                counts.setNewAnnotations(toolannos.size() - (counts.getExacts() + counts.getPartials()));
                counts.setMatches(partialMatchList);
                countsperpaper.put(key, counts);
            }
        }
        return countsperpaper;
    }

    /***
     * calculateAverageJaccards calculates the intersection and union of the superclasses for CRAFT and the tool GO:IDs.
     * It then gets the Jaccard values and averages them.
     * @param toolcounts - map containing the filename and the list of partial matches (GO:IDs)
     * @return map containing the filename and the average Jaccard value
     */
    private Map<String, Double> calculateAverageJaccards(Map<String, CountsAndPartials> toolcounts){
        Map<String, Double> averagejaccards = new HashMap<>();
        double[] jaccards;
        double mean, sum;
        List<PartialMatch> partialMatches;
        List<String> all, inbetween;
        String craftID, toolID;
        int arrIndex;

        //go through files and retrieve partial matches
        for(String key: toolcounts.keySet()){
            partialMatches = toolcounts.get(key).getMatches();
            jaccards = new double[partialMatches.size()];
            arrIndex = 0;
            sum = 0;
            all = new ArrayList<>();
            inbetween = new ArrayList<>();
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
                    //remove root from set
                    all.addAll(union);
                    all.remove("Thing");
                    inbetween.addAll(intersection);
                    inbetween.remove("Thing");
//                    System.out.println("Union: " + all);
//                    System.out.println("Intersection: " + inbetween);
                    //calculate jaccard values
                    jaccards[arrIndex] = (double)(inbetween.size()) / (double)(all.size());

                    //increase the index for jaccards
                    arrIndex++;
                }
                //calculate mean of jaccards
                for (int i = 0; i < jaccards.length; i++) {
                    sum += jaccards[i];
                }
                mean = sum/jaccards.length;
                //if the average is 0, then GO:ID has probably been removed/updated; don't add to map
                if(mean != 0.0) {
                    averagejaccards.put(key, mean);
                }
            }
        }
        return averagejaccards;
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
}
