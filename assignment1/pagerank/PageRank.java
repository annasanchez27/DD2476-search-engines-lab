package pagerank;

import java.util.*;
import java.io.*;
import java.util.Arrays;
import java.util.Random;

public class PageRank {

    /**  
     *   Maximal number of documents. We're assuming here that we
     *   don't have more docs than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     *   Mapping from document names to document numbers.
     */
	HashMap<Integer,String> davisTitles = new HashMap<Integer,String>();
	HashMap<Integer,String> wikiTitles = new HashMap<Integer,String>();

    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    /**  
     *   A memory-efficient representation of the transition matrix.
     *   The outlinks are represented as a HashMap, whose keys are 
     *   the numbers of the documents linked from.<p>
     *
     *   The value corresponding to key i is a HashMap whose keys are 
     *   all the numbers of documents j that i links to.<p>
     *
     *   If there are no outlinks from i, then the value corresponding 
     *   key i is null.
     */
    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();

    /**
     *   The number of outlinks from each node.
     */
    int[] out = new int[MAX_NUMBER_OF_DOCS];

    /**
     *   The probability that the surfer will be bored, stop
     *   following links, and take a random jump somewhere.
     */
    final static double BORED = 0.15;
	final static double C_CONSTANT = 0.85;

    /**
     *   Convergence criterion: Transition probabilities do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.00001;



	class Entry implements Comparable<Entry>{
		double  probability;
		int index;

		public Entry(double pr, int index) {
			this.probability = pr;
			this.index = index;
		}
		public Double getProb() {
			return this.probability;
		}

		public int getIndex() {
			return this.index;
		}
		@Override
		public int compareTo(Entry entry)
		{
			return Double.compare(this.probability,entry.getProb());
		}

	}

	/* --------------------------------------------- */
    public PageRank( String filename ) {
	int noOfDocs = readDocs( filename );
	//populate_davisTitle();
	populate_wikipedia();
	//If you want the written file with PageRank (no approximation)
	//double [] normalized_vector = iterate( noOfDocs, 1000 );
	//Print the 30 most important ones
	//top_ranking(30,normalized_vector);

	//Write all the ranking in a file
	//top_ranking_writefile(normalized_vector, "power_iterative_page.txt");


	//MonteCarlo algorithm 1
	double [] prob = monte_carlo_algorithm1(noOfDocs,noOfDocs*2);
	top_ranking_writefile_wiki(prob, "wikipedia_algorithm1.txt");

	//MonteCarlo algorithm 2
	//double [] prob2 = monte_carlo_algorithm2(noOfDocs,2);
	//top_ranking_writefile(prob2, "ranking_computed_algorithm2.txt");

	//MonteCarlo algorithm 4
	//double [] prob4 = monte_carlo_algorithm4(noOfDocs,4);
	//top_ranking_writefile(prob4, "ranking_computed_algorithm4.txt");

	//MonteCarlo algorithm 5
	//double [] prob5 = monte_carlo_algorithm5(noOfDocs,2);
	//top_ranking_writefile(prob5, "ranking_computed_algorithm5.txt");

	//write_file_plotgoodness(noOfDocs);




	}

    /* --------------------------------------------- */




	void write_file_plotgoodness(int noOfDocs){
		//Calculate goodness
		int [] N = new int[]{1,2,10,100};

		double [] plot = new double[4];
		double [] plot2 = new double[4];
		double [] plot4 = new double[4];
		double [] plot5 = new double[4];
		for(int i=0; i<N.length; i++) {
			int m = N[i];
			double [] normalized_vector = iterate( noOfDocs, 1000 );
			double [] prob = monte_carlo_algorithm1(noOfDocs,m*noOfDocs);
			double goodness = goodness_measure(normalized_vector, prob);
			plot[i] = goodness;
			System.out.println("DONE 1");

			double [] prob2 = monte_carlo_algorithm2(noOfDocs,m);
			double goodness2 = goodness_measure(normalized_vector, prob2);
			plot2[i] = goodness2;
			System.out.println("DONE 2");
			double [] prob4 = monte_carlo_algorithm4(noOfDocs,m);
			double goodness4 = goodness_measure(normalized_vector, prob4);
			plot4[i] = goodness4;
			System.out.println("DONE 4");

			double [] prob5 = monte_carlo_algorithm5(noOfDocs,m);
			double goodness5 = goodness_measure(normalized_vector, prob5);
			plot5[i] = goodness5;
			System.out.println("DONE 5");
		}
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter("plot_montecarlo", true));
			for(int i=0; i<4; i++) {
				writer.append(Double.toString(plot[i]));
			}
			writer.append("\n");

			for(int i=0; i<4; i++) {
				writer.append(Double.toString(plot2[i]));
			}
			writer.append("\n");

			for(int i=0; i<4; i++) {
				writer.append(Double.toString(plot4[i]));
			}
			writer.append("\n");

			for(int i=0; i<4; i++) {
				writer.append(Double.toString(plot5[i]));
			}
			writer.append("\n");

			writer.close();

		} catch (IOException ie) {
			System.out.println("An error occurred.");
			ie.printStackTrace();
		}

	}

    /**
     *   Reads the documents and fills the data structures. 
     *
     *   @return the number of documents read.
     */
    int readDocs( String filename ) {
	int fileIndex = 0;
	try {
	    System.err.print( "Reading file... " );
	    BufferedReader in = new BufferedReader( new FileReader( filename ));
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
		    if ( link.get(fromdoc) == null ) {
			link.put(fromdoc, new HashMap<Integer,Boolean>());
		    }
		    if ( link.get(fromdoc).get(otherDoc) == null ) {
			link.get(fromdoc).put( otherDoc, true );
			out[fromdoc]++;
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
	    System.err.println( "File " + filename + " not found!" );
	}
	catch ( IOException e ) {
	    System.err.println( "Error reading file " + filename );
	}
	System.err.println( "Read " + fileIndex + " number of documents" );
	return fileIndex;
    }


    /* --------------------------------------------- */


    /*
     *   Chooses a probability vector a, and repeatedly computes
     *   aP, aP^2, aP^3... until aP^i = aP^(i+1).
     */
    double [] iterate( int numberOfDocs, int maxIterations ) {
		//Create a vector with zeroes
		double [] old_vector = new double[numberOfDocs];
		Arrays.fill(old_vector, 0.0);
		//Set the probability to the first document to 1 (wild guess)
		old_vector[0] = 1;

		//Create a vector with the probability that the random surfer gets bored
		double bored_constant = (BORED/numberOfDocs);
		double diff = 100;
		int iterations = 0;
		while(diff > EPSILON && iterations <maxIterations) {
			double new_vector[] = new double[numberOfDocs];
			Arrays.fill(new_vector, bored_constant);
			for(HashMap.Entry<Integer,HashMap<Integer,Boolean>> entry : this.link.entrySet()){
				for(Integer key : entry.getValue().keySet()){
					new_vector[key] +=  old_vector[entry.getKey()]*(1-BORED)/out[entry.getKey()];
				}
			}
			diff = convergence_criteria(old_vector,new_vector);
			old_vector = new_vector.clone();
			iterations ++;
		}
		double [] normalized_vector = normalize_vector(old_vector);

		return normalized_vector;

    }

    public void top_ranking_writefile(double[] a, String title){
		ArrayList<Entry> list_entries = new ArrayList<>();
     	for(int i=0; i<a.length; i++){
    		Entry entry = new Entry(a[i],i);
    		list_entries.add(entry);
		}
		Collections.sort(list_entries);
     	Collections.reverse(list_entries);
		for(int i=0; i<a.length; i++){
			Entry e = list_entries.get(i);
			int ind = e.getIndex();
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(title, true));
				int doc_id = Integer.parseInt(docName[ind]);
				String ttitle = this.davisTitles.get(doc_id);
				writer.append(ttitle + ";" + a[ind] );
				writer.append("\n");
				writer.close();
			} catch (IOException ie) {
				System.out.println("An error occurred.");
				ie.printStackTrace();
			}

		}
	}

	public void top_ranking_writefile_wiki(double[] a, String title){
		ArrayList<Entry> list_entries = new ArrayList<>();
		for(int i=0; i<a.length; i++){
			Entry entry = new Entry(a[i],i);
			list_entries.add(entry);
		}
		Collections.sort(list_entries);
		Collections.reverse(list_entries);
		for(int i=0; i<a.length; i++){
			Entry e = list_entries.get(i);
			int ind = e.getIndex();
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(title, true));
				int doc_id = Integer.parseInt(docName[ind]);
				String ttitle = this.wikiTitles.get(doc_id);
				writer.append(ttitle + ";" + a[ind] );
				writer.append("\n");
				writer.close();
			} catch (IOException ie) {
				System.out.println("An error occurred.");
				ie.printStackTrace();
			}

		}
	}

	public double[] monte_carlo_algorithm4(int numberofDocs, int m){
		//N = number of random walks
		double [] last_doc_visited = new double[numberofDocs];
		int initial_docID = 0;
		for(int j=0; j<m; j++) {
			for (int i = 0; i <numberofDocs ; i++) {
				initial_docID = i;
				double [] updated = take_a_walk2(numberofDocs, initial_docID,last_doc_visited);
				last_doc_visited = updated.clone();
			}
		}
		for(int i=0; i<numberofDocs; i++){
			last_doc_visited[i] = last_doc_visited[i]/(m*numberofDocs);
		}
		return last_doc_visited;

	}

	public double[] monte_carlo_algorithm5(int numberofDocs, int m){
		//N = number of random walks
		double [] last_doc_visited = new double[numberofDocs];
		int initial_docID = 0;
		for(int j=0; j<m; j++) {
			for (int i = 0; i <numberofDocs ; i++) {
				initial_docID = (int) (Math.random() * numberofDocs);
				double [] updated = take_a_walk2(numberofDocs, initial_docID,last_doc_visited);
				last_doc_visited = updated.clone();
			}
		}
		for(int i=0; i<numberofDocs; i++){
			last_doc_visited[i] = last_doc_visited[i]/(m*numberofDocs);
		}
		return last_doc_visited;

	}

	public double[] take_a_walk2(int numberofDocs, int docID, double[] visited_docs_across_walks){
		//Input: document_ID where I will start my random walk
		//Output: document_ID where I finished my random walk

		//Stopping criteria:
		//1. You get bored
		//2. You have gone to all the documents

		HashMap<Integer,Boolean> visited_documents = new HashMap<Integer,Boolean>();
		visited_documents.put(docID,true);

		while(visited_documents.keySet().size()<numberofDocs) {
			double rand = Math.random();
			if (rand < C_CONSTANT) {
				//Get all the documents that are linked to our current document
				if (!link.containsKey(docID)) {
					return visited_docs_across_walks;
				}

				visited_documents.put(docID, true);
				visited_docs_across_walks[docID]++;
				HashMap<Integer, Boolean> outlinks = link.get(docID);
				int next_docIDX = (int) (Math.random() * outlinks.keySet().size());
				List<Integer> ids = new ArrayList<>(outlinks.keySet());
				docID = ids.get(next_docIDX);

			}
			else{
				return visited_docs_across_walks;
			}
		}
		return visited_docs_across_walks;
	}

	public int take_a_walk(int numberofDocs, int docID){
		//Input: document_ID where I will start my random walk
		//Output: document_ID where I finished my random walk

		//Stopping criteria:
		//1. You get bored
		//2. You have gone to all the documents

		HashMap<Integer,Boolean> visited_documents = new HashMap<Integer,Boolean>();
		visited_documents.put(docID,true);

		while(visited_documents.keySet().size()<numberofDocs) {
			double rand = Math.random();
			if (rand < C_CONSTANT) {
				//Get all the documents that are linked to our current document
				while(!link.containsKey(docID)){
					docID = (int) (Math.random() * numberofDocs);
				}
				visited_documents.put(docID,true);
				HashMap<Integer, Boolean> outlinks = link.get(docID);
				int next_docIDX = (int) (Math.random() * outlinks.keySet().size());
				List<Integer> ids = new ArrayList<>(outlinks.keySet());
				docID = ids.get(next_docIDX);
			} else {
				return docID;
			}
		}
		return docID;
	}

	public double[] monte_carlo_algorithm2(int numberofDocs, int m){
		//N = number of random walks
		double [] last_doc_visited = new double[numberofDocs];
		int initial_docID = 0;
		for(int j=0; j<m; j++) {
			for (int i = 0; i <numberofDocs ; i++) {
				initial_docID = i;
				int last_docID = take_a_walk(numberofDocs, initial_docID);
				last_doc_visited[last_docID]++;
			}
		}
		for(int i=0; i<numberofDocs; i++){
			last_doc_visited[i] = last_doc_visited[i]/(m*numberofDocs);
		}
		return last_doc_visited;

	}

	public double[] monte_carlo_algorithm1(int numberofDocs, int N){
    	//N = number of random walks
		double [] last_doc_visited = new double[numberofDocs];
		for(int i=0; i<N; i++){
			int initial_docID = (int) (Math.random() * numberofDocs);
			int last_docID = take_a_walk(numberofDocs,initial_docID);
			last_doc_visited[last_docID] ++;
		}
		for(int i=0; i<numberofDocs; i++){
			last_doc_visited[i] = last_doc_visited[i]/N;
		}
		return last_doc_visited;

	}

	public double goodness_measure(double[] pagerank, double[] monte_carlo ){
    	//Sort pagerank
		ArrayList<Entry> list_entries = new ArrayList<>();
		for(int i=0; i<pagerank.length; i++){
			Entry entry = new Entry(pagerank[i],i);
			list_entries.add(entry);
		}
		Collections.sort(list_entries);
		Collections.reverse(list_entries);

		//Sort montecarlo
		ArrayList<Entry> list_entries2 = new ArrayList<>();
		for(int i=0; i<monte_carlo.length; i++){
			Entry entry = new Entry(monte_carlo[i],i);
			list_entries2.add(entry);
		}
		Collections.sort(list_entries2);
		Collections.reverse(list_entries2);

		double sum = 0;
		for(int i=0; i<30; i++){
			Entry e = list_entries.get(i);
			int ind = e.getIndex();

			Entry e2 = list_entries2.get(i);
			int ind2 = e2.getIndex();

			sum += Math.pow((pagerank[ind]-monte_carlo[ind2]),2);
		}

		return sum;

	}




	public void top_ranking(int N, double[] a){
		ArrayList<Entry> list_entries = new ArrayList<>();
		for(int i=0; i<a.length; i++){
			Entry entry = new Entry(a[i],i);
			list_entries.add(entry);
		}
		Collections.sort(list_entries);
		Collections.reverse(list_entries);
		for(int i=0; i<N; i++){
			Entry e = list_entries.get(i);
			int ind = e.getIndex();

			System.out.println(docName[ind] + " (" + a[ind] + ")");
		}
	}

    public double[] normalize_vector(double [] a){
    	double sum = 0;
    	for(int i=0; i<a.length; i++){
    		sum += a[i];
		}
		for(int i=0; i<a.length; i++){
			a[i] = a[i]/sum;
		}
		return a;
	}




	public double convergence_criteria(double[] a, double[] new_a){
    	double sum = 0;
    	for (int i=0; i<new_a.length; i++){
    		double sub = Math.abs(a[i]-new_a[i]);
    		sum = sum + sub;
		}
    	return sum;
	}
    /* --------------------------------------------- */

	public void populate_davisTitle(){
		try {
			File myObj = new File("pagerank/davisTitles.txt");
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
	public void populate_wikipedia(){
		try{
		BufferedReader reader = new BufferedReader(new FileReader("pagerank/svwikiTitles.txt"));
		String line = reader.readLine();
		while (line != null) {
			String[] parts = line.split(";");
			int idx = Integer.parseInt(parts[0]);
			String  fileName  = parts[1];
			this.wikiTitles.put(idx, fileName);
			line = reader.readLine();
		}
		reader.close();
		} catch (IOException e ){
			e.printStackTrace();
		}

	}


    public static void main( String[] args ) {
	if ( args.length != 1 ) {
	    System.err.println( "Please give the name of the link file" );
	}
	else {

	    new PageRank( args[0] );
	}
    }
}
