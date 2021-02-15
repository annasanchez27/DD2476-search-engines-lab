/**
 *   Computes the Hubs and Authorities for an every document in a query-specific
 *   link graph, induced by the base set of pages.
 *
 *   @author Dmytro Kalpakchi
 */

package ir;
import java.util.*;
import java.io.*;


public class HITSRanker {

    /**
     *   Max number of iterations for HITS
     */
    final static int MAX_NUMBER_OF_STEPS = 1000;

    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     *   Convergence criterion: hub and authority scores do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.00001;

    /**
     *   The inverted index
     */
    Index index;

    HashMap<Integer,String> davisTitles = new HashMap<Integer,String>();


    /**
     *   Mapping from the titles to internal document ids used in the links file
     */
    //HashMap<String,Integer> titleToId = new HashMap<String,Integer>();

    /**
     *   Sparse vector containing hub scores
     */
    HashMap<Integer,Double> hubs;

    HashSet<Integer> documents_baseset = new HashSet<Integer>();
    /**
     *   Sparse vector containing authority scores
     */
    HashMap<Integer,Double> authorities;

    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    HashMap<Integer,HashMap<Integer,Boolean>> a_matrix = new HashMap<Integer,HashMap<Integer,Boolean>>();

    HashMap<Integer,HashMap<Integer,Boolean>> a_matrix_transpose = new HashMap<Integer,HashMap<Integer,Boolean>>();
    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph.
     * Each page is a node in graph with a distinct nodeID associated with it.
     * There is an edge between two nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     *  nodeID;outNodeID1,outNodeID2,...,outNodeIDK
     * This means that there are edges between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format:
     *  nodeID;pageTitle
     *  
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the same
     *       as docIDs used by search engine's Indexer
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     * @param      index           The inverted index
     */
    public HITSRanker( String linksFilename, String titlesFilename, Index index ) {
        this.index = index;
        readDocs( linksFilename );
        //this.a_matrix_transpose.remove(0);
        //this.a_matrix.get(3164).remove(0);
        populate_davisTitle(titlesFilename);

    }


    /* --------------------------------------------- */

    /**
     * A utility function that gets a file name given its path.
     * For example, given the path "davisWiki/hello.f",
     * the function will return "hello.f".
     *
     * @param      path  The file path
     *
     * @return     The file name.
     */
    private String getFileName( String path ) {
        String result = "";
        StringTokenizer tok = new StringTokenizer( path, "\\/" );
        while ( tok.hasMoreTokens() ) {
            result = tok.nextToken();
        }
        return result;
    }


    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param      linksFilename   File containing the links of the graph
     */
    int readDocs( String linksFilename) {
        int fileIndex = 0;
        try {
            System.err.print( "Reading file... " );
            BufferedReader in = new BufferedReader( new FileReader( linksFilename ));
            String line;
            while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
                int index = line.indexOf( ";" );
                String title = line.substring( 0, index );
                Integer fromdoc = docNumber.get( title );
                //  Have we seen this document before?
                if ( fromdoc == null ) {
                    // This is a previously unseen doc, so add it to the table.
                    fromdoc = fileIndex++;
                    docNumber.put( title, fromdoc );
                    docName[fromdoc] = title;
                }
                // Check all outlinks.
                StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
                while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
                    String otherTitle = tok.nextToken();
                    Integer otherDoc = docNumber.get( otherTitle );
                    if ( otherDoc == null ) {
                        // This is a previousy unseen doc, so add it to the table.
                        otherDoc = fileIndex++;
                        docNumber.put( otherTitle, otherDoc );
                        docName[otherDoc] = otherTitle;
                    }
                    // Set the probability to 0 for now, to indicate that there is
                    // a link from fromdoc to otherDoc.
                    if ( a_matrix.get(fromdoc) == null) {
                        a_matrix.put(fromdoc, new HashMap<Integer,Boolean>());
                    }
                    if ( a_matrix.get(fromdoc).get(otherDoc) == null) {
                        a_matrix.get(fromdoc).put( otherDoc, true );
                    }

                    if ( a_matrix_transpose.get(otherDoc) == null) {
                        a_matrix_transpose.put(otherDoc, new HashMap<Integer,Boolean>());
                    }
                    if ( a_matrix_transpose.get(otherDoc).get(fromdoc) == null) {
                        a_matrix_transpose.get(otherDoc).put( fromdoc, true );
                    }
                }
            }
            if ( fileIndex >= MAX_NUMBER_OF_DOCS ) {
                System.err.print( "stopped reading since documents table is full. " );
            }
            else {
                System.err.print( "done. " );
            }
        }
        catch ( FileNotFoundException e ) {
            System.err.println( "File " + linksFilename + " not found!" );
        }
        catch ( IOException e ) {
            System.err.println( "Error reading file " + linksFilename );
        }
        System.err.println( "Read " + fileIndex + " number of documents" );
        return fileIndex;
    }

    /**
     * Perform HITS iterations until convergence
     *
     * @param      titles  The titles of the documents in the root set
     */
    private void iterate(String[] titles) {
        initialize_algorithm();
        double conv1 = 100;
        double conv2 = 100;
        while(conv1>EPSILON || conv2 >EPSILON) {
            HashMap<Integer, Double> updated_hubs = update_hub();
            HashMap<Integer, Double> updated_auth = update_authoroties();
            conv1 = check_convergence(updated_auth,this.authorities);
            conv2 = check_convergence(updated_hubs,this.hubs);
            this.authorities = updated_auth;
            this.hubs = updated_hubs;
        }
    }

    public double check_convergence(HashMap<Integer, Double> vector1, HashMap<Integer, Double> vector2){
        double sum = 0.0;
        for(Integer base: this.documents_baseset){
            sum += Math.pow((vector1.get(base)-vector2.get(base)),2);
        }
        return sum;
    }

    public HashMap<Integer, Double> update_hub() {
        HashMap<Integer, Double> new_hubs = new HashMap<Integer, Double>();
        for (Integer ind :this.documents_baseset) {
                if(this.a_matrix.containsKey(ind)) {
                    HashMap<Integer, Boolean> col = this.a_matrix.get(ind);
                    double sum = 0;
                    for (Map.Entry<Integer, Boolean> e : col.entrySet()) {
                        Integer key = e.getKey();
                        if (this.authorities.containsKey(key)) {
                            sum = sum + this.authorities.get(key);
                        }
                    }
                    new_hubs.put(ind, sum);
                }
                else{
                    new_hubs.put(ind, 0.0);
                }
        }
        //Normalization
        double total = 0.0;
        for(Map.Entry<Integer, Double> norm : new_hubs.entrySet()){
            Double val = norm.getValue();
            total = total + Math.pow(val,2);
        }
        total = Math.sqrt(total);

        for(Map.Entry<Integer, Double> norm : new_hubs.entrySet()){
            norm.setValue(norm.getValue()/total);
        }
        return new_hubs;
    }

    public HashMap<Integer, Double> update_authoroties(){
        HashMap<Integer, Double> new_hubs = new HashMap<Integer, Double>();

        for (Integer ind :this.documents_baseset) {
            if(this.a_matrix_transpose.containsKey(ind)) {
                HashMap<Integer, Boolean> col = this.a_matrix_transpose.get(ind);
                double sum = 0;
                for (Map.Entry<Integer, Boolean> e : col.entrySet()) {
                    Integer key = e.getKey();
                    System.out.println(key);
                    if(this.hubs.containsKey(key)) {
                        sum = sum + this.hubs.get(key);
                    }
                }
                new_hubs.put(ind, sum);
            }else{
                new_hubs.put( ind,0.0);
            }

        }

        //Normalization
        double total = 0.0;
        for(Map.Entry<Integer, Double> norm : new_hubs.entrySet()){
            Double val = norm.getValue();
            total = total + Math.pow(val,2);
        }
        total = Math.sqrt(total);

        for(Map.Entry<Integer, Double> norm : new_hubs.entrySet()){
            norm.setValue(norm.getValue()/total);
        }
        return new_hubs;
    }

    public void initialize_algorithm(){
        this.authorities = new HashMap<>();
        this.hubs = new HashMap<>();
        for (Integer doc : this.documents_baseset) {
            this.authorities.put(doc,1.0);
            this.hubs.put(doc,1.0);
        }
    }

    /**
     * Rank the documents in the subgraph induced by the documents present
     * in the postings list `post`.
     *
     * @param      post  The list of postings fulfilling a certain information need
     *
     * @return     A list of postings ranked according to the hub and authority scores.
     */
    PostingsList rank(PostingsList post) {
        //
        // YOUR CODE HERE
        //
        return null;
    }


    /**
     * Sort a hash map by values in the descending order
     *
     * @param      map    A hash map to sorted
     *
     * @return     A hash map sorted by values
     */
    private HashMap<Integer,Double> sortHashMapByValue(HashMap<Integer,Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer,Double> > list = new ArrayList<Map.Entry<Integer,Double> >(map.entrySet());
      
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) { 
                    return (o2.getValue()).compareTo(o1.getValue()); 
                } 
            }); 
              
            HashMap<Integer,Double> res = new LinkedHashMap<Integer,Double>(); 
            for (Map.Entry<Integer,Double> el : list) { 
                res.put(el.getKey(), el.getValue()); 
            }
            return res;
        }
    } 


    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param      map        A hash map
     * @param      fname      The filename
     * @param      k          A number of entries to write
     */
    void writeToFile(HashMap<Integer,Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
            
            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer,Double> e : map.entrySet()) {
                    i++;
                    writer.write(docName[e.getKey()] + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k) break;
                }
            }
            writer.close();
        } catch (IOException e) {}
    }


    /**
     * Rank all the documents in the links file. Produces two files:
     *  hubs_top_30.txt with documents containing top 30 hub scores
     *  authorities_top_30.txt with documents containing top 30 authority scores
     */
    void rank(ArrayList<String> query) {
        Integer [] root_set = get_root_set(query);
        get_baseset(root_set);
        iterate(docNumber.keySet().toArray(new String[0]));
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }


    void rank_all_top30(){
        this.documents_baseset = new HashSet<>();
        String titles [] = docNumber.keySet().toArray(new String[0]);
        for (String title : titles) {
            documents_baseset.add(Integer.parseInt(title));
        }
        iterate(titles);
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }

    public Integer [] get_root_set(ArrayList<String> query){
        PostingsList p1 = index.getPostings(query.get(0));
        for(int i=1;i<query.size();i++){
            PostingsList p2 = index.getPostings(query.get(i));
            p1 = union(p1,p2);
        }
        Integer [] internal_ids = new Integer[p1.getList().size()];
        for(int i=0; i<p1.getList().size(); i++){
            PostingsEntry p_entry = p1.get(i);
            int internal_id = this.docNumber.get(p_entry.docID);
            internal_ids[i] = internal_id;
        }
        return internal_ids;
    }

    public void get_baseset(Integer [] root_set){
        for(int i=0; i<root_set.length; i++){
            HashMap<Integer,Boolean> links = a_matrix.get(root_set[i]);
            for (Map.Entry<Integer,Boolean> entry : links.entrySet()){
                documents_baseset.add(entry.getKey());
            }
            HashMap<Integer,Boolean> links_transposed = a_matrix_transpose.get(root_set[i]);
            for (Map.Entry<Integer,Boolean> entry2 : links_transposed.entrySet()){
                documents_baseset.add(entry2.getKey());
            }
        }
    }





    public void populate_davisTitle(String title){
        try {
            File myObj = new File(title);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String [] read_data = data.split(";");
                this.davisTitles.put(Integer.parseInt(read_data[0]), read_data[1]);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }





    public PostingsList union(PostingsList p1, PostingsList p2){
        PostingsList p3 = new PostingsList();
        int  l1 = p1.size();
        int l2 = p2.size();
        int i1 = 0;
        int i2 = 0;
        if (l1>0 && l2>0){
            while (i1 < l1 && i2 < l2) {
                PostingsEntry pp1 = p1.get(i1);
                PostingsEntry pp2 = p2.get(i2);
                if (pp1.docID == pp2.docID) {
                    pp1.sum_score_toentry(pp2.score);
                    p3.set(pp1);
                    i1 = i1 + 1;
                    i2 = i2 + 1;

                } else if (pp1.docID < pp2.docID) {
                    p3.set(pp1);
                    i1 = i1 + 1;
                } else {
                    p3.set(pp2);
                    i2 = i2 + 1;
                }
            }
            if (i1 < l1) {
                while(i1<l1){
                    PostingsEntry pp1 = p1.get(i1);
                    p3.set(pp1);
                    i1 = i1 + 1;
                }
            } else if (i2 < l2) {
                while(i2<l2){
                    PostingsEntry pp2 = p2.get(i2);
                    p3.set(pp2);
                    i2 = i2 + 1;
                }
            }
        }
        return p3;
    }



    /* --------------------------------------------- */


    public static void main( String[] args ) {
        if ( args.length != 2 ) {
            System.err.println( "Please give the names of the link and title files" );
        }
        else {
            HITSRanker hr = new HITSRanker( args[0], args[1], null );
            //ArrayList<String> query = new ArrayList<String>();
            //hr.rank(query);
            hr.rank_all_top30();

        }
    }
} 