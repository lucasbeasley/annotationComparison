import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Lucas Beasley
 * 10/30/17
 * Counts annotations in a file and compares annotations against CRAFT annotations.
 */
public class annotationCount {
    public static class Annotation{
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

    public static void main(String[] args) {
        //Directories for CRAFT annotations
        File craft_cc = new File("craftAnnotations/go_cc");
        File craft_bpmf = new File("craftAnnotations/go_bpmf");
        //Directory for NCBO Annotations
        File ncbo = new File("ncboAnnotations");
        //Directory for Textpresso Annotations
        File textpresso = new File("textpressoAnnotations");

        //Pull all annotations into maps
        Map<String, List<Annotation>> craft_go_cc_annos = pullCRAFTAnnos(craft_cc);
        Map<String, List<Annotation>> craft_go_bpmf_annos = pullCRAFTAnnos(craft_bpmf);
        Map<String, List<Annotation>> craft_annos = mapMerge(craft_go_cc_annos, craft_go_bpmf_annos);
        Map<String, List<Annotation>> ncbo_annos = pullAnnos(ncbo);
        Map<String, List<Annotation>> textpresso_annos = pullAnnos(textpresso);

        //compare CRAFT annotations to tool annotations
        Map<String, int[]> ncbo_counts = annotationCounter(craft_annos, ncbo_annos);
        Map<String, int[]> textpresso_counts = annotationCounter(craft_annos, textpresso_annos);
        //get total number of annotations for CRAFT per file
        Map<String, Integer> craft_total_count = totalAnnosInMap(craft_annos);

        Map<String, double[]> ncbo_accuracies = annotationAccuracy(ncbo_counts, craft_total_count);
        Map<String, double[]> textpresso_accuracies = annotationAccuracy(textpresso_counts, craft_total_count);
        boolean bool = true;
    }

    /***
     * pullCRAFTAnnos retrieves the annotations from the annotation files in the passed directory.
     * @param annoDirectory - directory of CRAFT annotations
     * @return map with the filename as a key and a list of its respective annotations as a value
     */
    private static Map<String, List<Annotation>> pullCRAFTAnnos(File annoDirectory){
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
    private static Map<String, List<Annotation>> mapMerge(Map<String, List<Annotation>> map1, Map<String, List<Annotation>> map2){
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
    private static Map<String, List<Annotation>> pullAnnos(File annoDirectory){
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
     * annotationCounter counts the number of total and partial annotation matches for a tool vs. the CRAFT corpus.
     * Annotation matches are based upon GO:ID match and beginning and ending indexes for a term.
     * @param craft - map of CRAFT annotations to each file
     * @param tool - map of tool annotations to each file
     * @return map of total and partial annotation match counts to each file
     */
    private static Map<String, int[]> annotationCounter(Map<String, List<Annotation>> craft, Map<String,List<Annotation>> tool){
        Map<String, int[]> countsperpaper = new HashMap<>();
        int[] counts;
        List<Annotation> craftannos, toolannos;

        //pull craft keys and lists
        for(String key: craft.keySet()){
            craftannos = craft.get(key);
            counts = new int[2]; //0 = total match; 1 = partial match.
            //check if tool contains key
            if(tool.containsKey(key)){
                //pull list of annos and check against craft
                toolannos = tool.get(key);
                for(Annotation a: toolannos){
                    for(Annotation b: craftannos){
                        //if both contain same indexes
                        if(a.getStartIndex() == b.getStartIndex() && a.getEndIndex() == b.getEndIndex()){
                            //if both contain same GO:IDs
                            if(a.getID().equals(b.getID())){
                                counts[0]++; //both same, add to total match count
                            }
                            else{
                                counts[1]++; //same ID, add to partial
                            }
                        }
                        else if(a.getID().equals(b.getID())){
                            counts[1]++; //same indexes, add to partial
                        }
                    }
                }
                countsperpaper.put(key, counts);
            }
        }
        return countsperpaper;
    }

    /***
     * totalAnnosInMap counts the total number of annotations for each paper.
     * @param annotationMap - map of annotations to files
     * @return map of total annotations for each file
     */
    private static Map<String, Integer> totalAnnosInMap(Map<String, List<Annotation>> annotationMap){
        int total;
        List<Annotation> annos;
        Map<String, Integer> counts = new HashMap<>();

        for(String key: annotationMap.keySet()){
            annos = annotationMap.get(key);
            total = annos.size();
            counts.put(key, total);
        }
        return counts;
    }

    /***
     * annotationAccuracy returns the total and partial accuracies for a tool vs. the CRAFT corpus.
     * @param tool_counts - map of total and partial annotation matches per file for a tool
     * @param craft_counts - total number of annotations per file for the CRAFT corpus
     * @return map of all total and partial accuracies per file for a tool
     */
    private static Map<String, double[]> annotationAccuracy(Map<String, int[]> tool_counts, Map<String, Integer> craft_counts){
        Map<String, double[]> all_accuracies = new HashMap<>();
        double[] accuracies;
        int[] annocounts;
        int crafttotal;
        double partial, total;

        for(String key: tool_counts.keySet()){
            accuracies = new double[2];
            annocounts = tool_counts.get(key);
            //compute accuracies tool
            if(craft_counts.containsKey(key)){
                crafttotal = craft_counts.get(key);
                total = (double)(annocounts[0]/crafttotal); //not precise*******
                partial = (double)(annocounts[1]/crafttotal);
                accuracies[0] = total;
                accuracies[1] = partial;
            }
            //if CRAFT doesn't contain file, set accuracy to -1
            else{
                accuracies[0] = -1;
                accuracies[1] = -1;
            }
            all_accuracies.put(key, accuracies);
        }
        return all_accuracies;
    }
}
