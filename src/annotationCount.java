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

        Map<String, List<Annotation>> craft_go_cc_annos = pullCRAFTAnnos(craft_cc);
        Map<String, List<Annotation>> craft_go_bpmf_annos = pullCRAFTAnnos(craft_bpmf);
        Map<String, List<Annotation>> craft_annos = mapMerge(craft_go_cc_annos, craft_go_bpmf_annos);
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

                        //cycle through double quotes to get to the second end index
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
}
