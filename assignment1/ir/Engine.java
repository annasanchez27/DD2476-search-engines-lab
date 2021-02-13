/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */

package ir;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.io.File;
import java.util.HashMap;
import java.util.Scanner;

/**
 *  This is the main class for the search engine.
 */
public class Engine {

    /** The inverted index. */
    //Index index = new HashedIndex();
    Index index = new PersistentHashedIndex();
    // Index index = new PersistentScalableHashedIndex();
    // Index index = new PersistentScalableHashedIndexSec();

    /** The indexer creating the search index. */
    Indexer indexer;

    /** K-gram index */
    KGramIndex kgIndex;

    /** The searcher used to search the index. */
    Searcher searcher;

    /** Spell checker */
    SpellChecker speller;

    /** The engine GUI. */
    SearchGUI gui;

    /** Directories that should be indexed. */
    ArrayList<String> dirNames = new ArrayList<String>();

    /** Lock to prevent simultaneous access to the index. */
    Object indexLock = new Object();

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file = null;

    /** The file containing the logo. */
    String pic_file = "";

    /** The file containing the pageranks. */
    String rank_file = "";

    /** For persistent indexes, we might not need to do any indexing. */
    boolean is_indexing = true;

    HashMap<Integer,Double> ranking_hash = new HashMap<Integer,Double>();
    HashMap<String,Double> title_hash = new HashMap<String,Double>();


    /* ----------------------------------------------- */


    /**  
     *   Constructor. 
     *   Indexes all chosen directories and files
     */
    public Engine( String[] args ) {
        HashMap<String, Integer> myNewHashMap = new HashMap<>();
        for(HashMap.Entry<Integer, String> entry : index.docNames.entrySet()){
            myNewHashMap.put(entry.getValue().split("/davisWiki/")[1], entry.getKey());
        }

        //read the ranking
        try {

            File myObj = new File("ranking_computed.txt");
            Scanner myReader = new Scanner(myObj);

            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String [] read_data = data.split(";");
                this.title_hash.put(read_data[0], Double.parseDouble(read_data[1]));
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        for (HashMap.Entry<String, Double> entry : this.title_hash.entrySet()) {
            if(myNewHashMap.containsKey(entry.getKey())) {
                int index = myNewHashMap.get(entry.getKey());
                this.ranking_hash.put(index, entry.getValue());
            }
        }


        decodeArgs( args );
        indexer = new Indexer( index, kgIndex, patterns_file );
        searcher = new Searcher( index, kgIndex ,ranking_hash);
        gui = new SearchGUI( this );
        gui.init();
        /* 
         *   Calls the indexer to index the chosen directory structure.
         *   Access to the index is synchronized since we don't want to 
         *   search at the same time we're indexing new files (this might 
         *   corrupt the index).
         */




        if (is_indexing) {
            synchronized ( indexLock ) {
                gui.displayInfoText( "Indexing, please wait..." );
                long startTime = System.currentTimeMillis();
                for ( int i=0; i<dirNames.size(); i++ ) {
                    File dokDir = new File( dirNames.get( i ));
                    indexer.processFiles( dokDir, is_indexing );
                }
                long elapsedTime = System.currentTimeMillis() - startTime;
                gui.displayInfoText( String.format( "Indexing done in %.1f seconds.", elapsedTime/1000.0 ));
                System.out.println("BEFORE LAST CLEAN UP");
                index.cleanup();
                System.out.println("AFTER LAST CLEAN UP");

            }
        } else {
            gui.displayInfoText( "Index is loaded from disk" );
        }
    }


    /* ----------------------------------------------- */

    /**
     *   Decodes the command line arguments.
     */
    private void decodeArgs( String[] args ) {
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-d".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    dirNames.add( args[i++] );
                }
            } else if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    patterns_file = args[i++];
                }
            } else if ( "-l".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    pic_file = args[i++];
                }
            } else if ( "-r".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    rank_file = args[i++];
                }
            } else if ( "-ni".equals( args[i] )) {
                i++;
                is_indexing = false;
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }                   
    }


    /* ----------------------------------------------- */


    public static void main( String[] args ) {
        Engine e = new Engine( args );
    }

}

