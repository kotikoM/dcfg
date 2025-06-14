package dk; 
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import grammar.Grammar;

import static util.Logger.log;

public class GenerateAutomaton {
    private static final String BINARYFILE = "src/main/java/dk/DK1.bin";
    private static DK1 AUTOMATON;
    private static final String TerminalsFile = "src/main/java/dk/DoNotTouchGrammar.txt";
    private static final String GrammarFile = "src/main/java/dk/DoNotTouchTerminals.txt";

    
    public static DK1 generateAutomaton(String grammarFilePath, String terminalFilePath, Grammar g){
        
        String modifiedGrammar = readFromFile(grammarFilePath);
        String modifiedTerminals = readFromFile(terminalFilePath);

        String oldGrammar = readFromFile(GrammarFile);
        String oldTerminals = readFromFile(TerminalsFile);

        boolean modified = false;

        if (!modifiedGrammar.equals(oldGrammar)){
            modified = true;
            writeToFile(modifiedGrammar, GrammarFile);
        } 

        if (modifiedTerminals.equals(oldTerminals)){
            modified = true;
            writeToFile(modifiedTerminals, TerminalsFile);
        }

        if (modified){
            generateForGrammar(g);
        }
        else {
            loadFromBinary();
        }

        return AUTOMATON; 
    }

    public static String readFromFile(String fileName) {
        try {
            return Files.readString(Paths.get(fileName));
        }
        catch(IOException e){
            System.out.println("Error reading file: " + e.getMessage());
            return "";
        }
    }  

    public static void writeToFile(String content, String fileName) {
        try {
            Files.writeString(Paths.get(fileName), content);
        }    
        catch(IOException e){
            System.out.println("Error writing file: " + e.getMessage());
            
        }
    }

    public static void generateForGrammar(Grammar g){

        long startTime = System.currentTimeMillis();
        AUTOMATON = new DK1(g);
        long endTime = System.currentTimeMillis();

        System.out.println("Generation completed in " + (endTime - startTime) + "ms");
        log("number of states: " + AUTOMATON.getStates().size());
        log("-----------------------");
        log("DK1 test passed = " + AUTOMATON.dk1Test());
        log("-----------------------");
        log("\n");

        saveToBinaryFile();
    }
    

    public static void loadFromBinary() {
        
        long startTime = System.currentTimeMillis();

        try {
            FileInputStream fis = new FileInputStream(BINARYFILE);
            ObjectInputStream ois = new ObjectInputStream(fis);

            Object obj = ois.readObject();
            if (obj instanceof DK1){
                AUTOMATON = (DK1) obj;
            } 
        } 
        catch (IOException | ClassNotFoundException e){
            System.err.println("Error loading from binary: " + e.getMessage());
            e.printStackTrace();
            
        }

        long endTime = System.currentTimeMillis();
        
        System.out.println("Loading completed in " + (endTime - startTime) + "ms");
        log("number of states: " + AUTOMATON.getStates().size());
        log("-----------------------");
        log("\n");
    }

    private static void saveToBinaryFile(){
        try (FileOutputStream fos = new FileOutputStream(BINARYFILE);
            ObjectOutputStream oos = new ObjectOutputStream(fos)){
                oos.writeObject(AUTOMATON);
                oos.flush();
            } 
        catch (IOException e){
            System.err.println("Error saving to binary: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save automaton to binary", e);
        }
    }
    

    public static void main(String args[]) throws Exception{
        String grammarFilePath = "src/main/java/grammar/Grammar.txt";
        String terminalsFilePath = "src/main/java/grammar/Terminals.txt";
        Grammar g = new Grammar(grammarFilePath, terminalsFilePath);
        
        // GenerateAutomaton.generateForGrammar(g);
        GenerateAutomaton.loadFromBinary();
        DK1 dk1 = GenerateAutomaton.AUTOMATON;
        // System.out.println(dk1);

    }
    
    
}

