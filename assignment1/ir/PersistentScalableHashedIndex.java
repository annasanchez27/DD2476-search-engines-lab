/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, KTH, 2018
 */

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks.
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentScalableHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME_FINAL = "dictionaryfinal";

    public static final String DICTIONARY_FNAME = "dictionary";
    /** The dictionary file name */

    public static final String DATA_FNAME = "data";
    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;


    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile_final;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile_final;

    /** Pointer to the first free memory cell in the data file. */
    long free_final = 0L;

    long free = 0L;

    HashSet<Long> hashing_used = new HashSet<Long>();


    /** The cache as a main-memory hash map. */
    TreeMap<String,PostingsList> index = new TreeMap<String,PostingsList>();

    int MAXIMUM_TOKENS = 75000;

    int tokens_read = 0;

    int steps = 0;

    boolean first = true;
    int pointer_finaldocument = 0;
    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */
    public class EntryDataFile {

        public String token;

        public PostingsList postingsList = new PostingsList();


        private PostingsList fromStringtoEntry(String postlist_str){
            String[] first_posting_list = postlist_str.split("\\*");
            token = first_posting_list[0];
            String[] posting_list = first_posting_list[1].split("-");
            for(int i=0 ; i < posting_list.length ; i++){
                String post = posting_list[i];
                String [] post2 = post.split(":");
                String docID = post2[0];
                String[] offset_stringlist = post2[1].split(",");
                ArrayList<Integer> offsetList = new ArrayList<>();
                for(int j=0; j<offset_stringlist.length; j++){
                    String offset_str = offset_stringlist[j];
                    offsetList.add(Integer.parseInt(offset_str));
                }
                PostingsEntry pentry = new PostingsEntry();
                pentry.docID = Integer.parseInt(docID);
                pentry.offsetList = offsetList;
                postingsList.set(pentry);
            }

            return postingsList;
        }



        public String fromEntryoString(){
            ArrayList<String> final_array = new ArrayList<>();
            for(int i=0;i<postingsList.size();i++){
                PostingsEntry postentry = postingsList.get(i);
                String docID = String.valueOf(postentry.docID);
                ArrayList<String> offset_array = new ArrayList<>();
                for(int j=0;j<postentry.offsetList.size();j++){
                    int offset = postentry.offsetList.get(j);
                    String offset_s = String.valueOf(offset);
                    offset_array.add(offset_s);
                }
                String offset_string = String.join(",",offset_array);
                String entry_string = docID +":"+ offset_string;
                final_array.add(entry_string);

            }
            String final_string = String.join("-",final_array);
            return token + "*" + final_string;
        }
    }

    public class Entry{
        private long pointer;
        public int length_entrydata;
        public long getPointer(){
            return pointer;
        }
        public void setPointer(long p){
            pointer = p;
        }
    }

    // ==================================================================


    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created.
     */
    public PersistentScalableHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */
    int writeData(RandomAccessFile dataFi, String dataString, long ptr ) {
        try {
            dataFi.seek( ptr+4);
            byte[] data = dataString.getBytes();
            dataFi.write(data);
            dataFi.seek(ptr);
            dataFi.writeInt(data.length);
            return data.length + 4;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }

    int writeData2(RandomAccessFile dataFi, String dataString, long ptr ) {
        try {
            dataFi.seek( ptr);
            byte[] data = dataString.getBytes();
            dataFi.write(data);

            return data.length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     *  Reads data from the data file
     */
    String readData(RandomAccessFile file ,long ptr, int size) {
        try {
            byte[] data = new byte[size];
            file.seek(ptr + 4);
            file.readFully(data);
            String r = new String(data);
            return r;
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }



    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" , true);
        for (Map.Entry<Integer,String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
    }


    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    private void readDocInfo() throws IOException {
        System.out.println("DOCINFO");

        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try (BufferedReader br = new BufferedReader(freader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put(new Integer(data[0]), data[1]);
                docLengths.put(new Integer(data[0]), new Integer(data[2]));
            }
        }
        freader.close();
    }

    /**
     *  Write the index to files.
     */
    public void writeIndex(boolean first) {
        String name = "";
        if(first){
            name =  INDEXDIR + "/" + DATA_FNAME + "M" + String.valueOf(steps) ;
        }else{
            name =  INDEXDIR + "/" + DATA_FNAME + String.valueOf(steps) ;
        }
        try {
            writeDocInfo();
            long free2 = 0;
            RandomAccessFile d =  new RandomAccessFile(name, "rw" );
            // Write the dictionary and the postings list
            for(Map.Entry<String,PostingsList> element : index.entrySet()){
                EntryDataFile entrydata = new EntryDataFile();
                entrydata.token = element.getKey();
                entrydata.postingsList = element.getValue();
                String serialized = entrydata.fromEntryoString();
                int readbytes = writeData(d,serialized,free2); //TESTED AND WELL
                if (readbytes < 0) {
                    continue;
                }
                free2 = free2 + readbytes + 1;
                }
            d.close();

        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    public PostingsList getPostingsMemory( String token ) {
        PostingsList post_list;
        post_list = index.get(token);
        return post_list;
    }

    // ==================================================================

    Entry readEntry( long ptr ) {
        Entry en = new Entry();
        try {
            dictionaryFile.seek(ptr);
            long pointer = dictionaryFile.readLong();
            dictionaryFile.seek(ptr+8);
            int num_bytes = dictionaryFile.readInt();
            en.setPointer(pointer);
            en.length_entrydata = num_bytes;

        }
        catch (IOException e){};
        return en;
    }




    String readData2( long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }


    public PostingsList getPostings( String token ) {
        try{
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
            RandomAccessFile dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );

            long hash = hashCode2(token);
            String token_found = "";
            String s = "";
            while(!token.equals(token_found)){
                Entry e = readEntry(hash);
                long pointer = e.getPointer();
                int bytes_data = e.length_entrydata;
                s = readData2(pointer,bytes_data);
                token_found = fromStringtoToken(s);
                hash = hash + 12;
            }
            EntryDataFile entry = new EntryDataFile();
            PostingsList plist = entry.fromStringtoEntry(s);
            return plist;
        }
        catch (IOException ie){return null;}
    }

    public String fromStringtoToken(String s){
        return s.split("\\*")[0];
    }

    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        if(tokens_read<MAXIMUM_TOKENS){
            PostingsList post_list = getPostingsMemory(token);
            PostingsEntry post_entry = new PostingsEntry();
            post_entry.docID = docID;
            post_entry.offsetList.add(offset);
            if (post_list == null) {
                PostingsList p_list = new PostingsList();
                p_list.set(post_entry);
                index.put(token, p_list);
                tokens_read++;
            } else {
                post_list.set(post_entry);
            }

        }
        else {
            write_everything();
            insert(token,docID,offset);

        }

    }
    public int writePostingList(RandomAccessFile file,PostingsList p1, String token,long pointer) {
        EntryDataFile entrydata = new EntryDataFile();
        entrydata.token = token;
        entrydata.postingsList = p1;
        String serialized = entrydata.fromEntryoString();
        int readbytes = writeData(file,serialized,pointer);
        return readbytes;
        }
    public void merge() {
        try {
            int i = 0;
            long pointer1 = 0; //Pointer to read first file
            long pointer2 = 0; //Pointer to read second file
            long pointer3 = 0; //Pointer to write on the third file
            RandomAccessFile dataM = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + "M" + String.valueOf(steps - 1), "rw");
            RandomAccessFile data = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + String.valueOf(steps - 1), "rw");
            RandomAccessFile dataNext = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME +"M"+ String.valueOf(steps), "rw");
            long  l1 = dataM.length();
            long l2 = data.length();


            while (pointer1 < l1 && pointer2 < l2) {
                i++;
                //Doc1

                dataM.seek(pointer1);
                int size10 = dataM.readInt();

                String data_given = readData(dataM,pointer1,size10);
                int readbytes1 = size10 + 4;
                String token1 = fromStringtoToken(data_given);
                EntryDataFile e1 = new EntryDataFile();
                PostingsList p1 = e1.fromStringtoEntry(data_given);

                //Doc2
                data.seek(pointer2);
                int size20 = data.readInt();
                String data2 = readData(data,pointer2,size20);
                int readbytes2 = size20 + 4;
                String token2 = fromStringtoToken(data2);
                EntryDataFile e2 = new EntryDataFile();
                PostingsList p2 = e2.fromStringtoEntry(data2);

                if (token1.compareTo(token2)==0) {
                    PostingsList p3 = intersection(p1,p2);
                    int read = writePostingList(dataNext,p3,token1,pointer3);
                    pointer3 = pointer3 + read + 1;
                    pointer1 = pointer1 +readbytes1 + 1;
                    pointer2 = pointer2 + readbytes2 + 1;
                }
                else if (token1.compareTo(token2)<0) {
                    int read = writePostingList(dataNext,p1,token1,pointer3);
                    pointer3 = pointer3 + read + 1;
                    pointer1 = pointer1 + readbytes1 + 1;
                } else if(token1.compareTo(token2)>0) {
                    int read = writePostingList(dataNext,p2,token2,pointer3);
                    pointer3 = pointer3 + read + 1;
                    pointer2 = pointer2 + readbytes2+ 1;
                }
            }

            if (pointer1 < l1) {
                while(pointer1<l1) {
                    dataM.seek(pointer1);
                    int size1 = dataM.readInt();
                    String data_n = readData(dataM,pointer1,size1);
                    int readbytes1 = size1 + 4;
                    pointer1 = pointer1 + readbytes1 + 1;
                    String token1 = fromStringtoToken(data_n);
                    EntryDataFile e1 = new EntryDataFile();
                    PostingsList p1 = e1.fromStringtoEntry(data_n);
                    int read = writePostingList(dataNext,p1,token1,pointer3);
                    pointer3 = pointer3 + read + 1;

                }
            } else if (pointer2 < l2) {
                while(pointer2<l2) {
                    data.seek(pointer2);
                    int size2 = data.readInt();
                    String data2 = readData(data,pointer2,size2);
                    int readbytes2 = size2 + 4;
                    pointer2 = pointer2 + readbytes2 + 1;
                    String token2 = fromStringtoToken(data2);
                    EntryDataFile e2 = new EntryDataFile();
                    PostingsList p2 = e2.fromStringtoEntry(data2);
                    int read = writePostingList(dataNext,p2,token2,pointer3);
                    pointer3 = pointer3 + read + 1;
                }
            }

        }
        catch (IOException ex ){}
    }

    public PostingsList intersection(PostingsList p1, PostingsList p2){
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
                    ArrayList<Integer> offsetList1 = pp1.offsetList;
                    ArrayList<Integer> offsetList2 = pp2.offsetList;
                    ArrayList<Integer> result = merge_offset(offsetList1,offsetList2);
                    PostingsEntry postr = new PostingsEntry();
                    postr.docID = pp1.docID;
                    postr.offsetList = result;
                    if(result.size()>0){
                        p3.set(postr);
                    }
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
                p3.getList().addAll(p1.getList().subList(i1, l1));
            } else if (i2 < l2) {
                p3.getList().addAll(p2.getList().subList(i2, l2));
            }
        }
        return p3;
    }
    public ArrayList<Integer> merge_offset(ArrayList<Integer> offsetList1, ArrayList<Integer> offsetList2){
        ArrayList<Integer> p3 = new ArrayList<Integer>();
        int  l1 = offsetList1.size();
        int l2 = offsetList2.size();
        int i1 = 0;
        int i2 = 0;
        if (l1>0 && l2>0){
            while (i1 < l1 && i2 < l2) {
                int num1 = offsetList1.get(i1);
                int num2 = offsetList2.get(i2);
                if (num1==num2) {
                    p3.add(num2);
                    i1 = i1 + 1;
                    i2 = i2 + 1;

                } else if (num1 < num2) {
                    p3.add(num1);
                    i1 = i1 + 1;
                } else {
                    p3.add(num2);
                    i2 = i2 + 1;
                }
            }
        }
        if (i1 < l1) {
            p3.addAll(offsetList1.subList(i1, l1));
        } else if (i2 < l2) {
            p3.addAll(offsetList2.subList(i2, l2));
        }
        //System.out.println("offset list found");
        return p3;



    }


    /**
     *  Write index to file after indexing is done.
     */
    public void write_everything(){
        if(first){
            writeIndex(first);
            first = false;
            System.err.println( index.size() + " unique words" );
            System.err.print( "Writing index to disk..." );
            System.err.println( "done!" );
            docLengths.clear();
            docNames.clear();
            index.clear();
            tokens_read = 0;

        }else{
            writeIndex(first);
            docLengths.clear();
            docNames.clear();
            index.clear();
            tokens_read = 0;
            steps ++;
            //Merge both files
            merge();
            System.out.println("Merged");
        }



    }
    public void writeFinal(){
        int pointer = 0; //Pointer of the first file
        int pointer_f = 0; //Pointer of the FINAL file
        System.out.println("HELLOOO");
        try {
            System.out.println(INDEXDIR + "/" + DATA_FNAME +"M"+ String.valueOf(steps));
            RandomAccessFile dataNext = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME +"M"+ String.valueOf(steps), "rw");

            long l1 = dataNext.length();
            while(pointer<l1){
                dataNext.seek(pointer);
                int size = dataNext.readInt();
                String data_n = readData(dataNext,pointer,size);
                int readbytes1 = size + 4;
                pointer = pointer + readbytes1 + 1;

                EntryDataFile entry = new EntryDataFile();
                PostingsList p1 = entry.fromStringtoEntry(data_n);
                String token = entry.token;


                int written = writeData2(dataFile,data_n,pointer_f);
                long hashed = hashCode2(token);

                Entry e_dict = new Entry();
                e_dict.pointer = pointer_f;
                e_dict.length_entrydata = written;
                while(hashing_used.contains(hashed)){
                    hashed +=12;
                }
                writeEntry(e_dict,hashed);
                hashing_used.add(hashed);
                pointer_f = pointer_f + written + 1;
            }

        }catch(IOException ie){}



    }
    public void cleanup() {
        writeIndex(first);
        steps ++;
        docLengths.clear();
        docNames.clear();
        index.clear();
        tokens_read = 0;
        System.out.println("BEFORE MERGE");
        merge();
        System.out.println("AFTER MERGE");
        writeFinal();


    }
    public long hashCode2(String word) {
        long hashed = word.hashCode() & 0xfffffff;
        return  hashed % TABLESIZE *12;
    }
    void writeEntry( Entry entry, long ptr ) {
        try {
            dictionaryFile.seek(ptr);
            dictionaryFile.writeLong(entry.getPointer());
            dictionaryFile.seek(ptr+8);
            dictionaryFile.writeInt(entry.length_entrydata);
        }
        catch (IOException e){};
    }

}