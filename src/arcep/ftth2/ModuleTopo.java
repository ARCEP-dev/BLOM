/*
 * Copyright (c) 2025, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package arcep.ftth2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import arcep.ftth2.configuration.Configuration;
import arcep.utilitaires.monitoring.MonitoredThread;

public class ModuleTopo {
    
    final private String cheminReseau;
    final private String adresseLP, adresseSR, adresseNRA, adresseCollecte;
    final private String adresseDptsLimitrophes;
    final private String adresseShapesDptsMetro, nameShapeDpts;
    final private String[] shapesDOM, fichiersZonage;

    private Map<String,List<String>> dptsLimitrophes;
    
    public ModuleTopo(String[] fichiersCuivre, String adresseDptsLimitrophes, String adresseShapesDptsMetro, String[] shapesDOM, String nameShapeDpts,
            String[] fichiersZonage){
        this.cheminReseau = Configuration.get().cheminReseau;
        this.adresseLP = fichiersCuivre[0];
        this.adresseSR = fichiersCuivre[1];
        this.adresseNRA = fichiersCuivre[2];
        this.adresseCollecte = fichiersCuivre[3];
        this.adresseDptsLimitrophes = adresseDptsLimitrophes;
        this.adresseShapesDptsMetro = adresseShapesDptsMetro;
        this.shapesDOM = shapesDOM;
        this.nameShapeDpts = nameShapeDpts;
        this.fichiersZonage = fichiersZonage;
    }
    
    public void readDptsLimitrophes(){
        String ligne;
        String[] donneesLigne;
        dptsLimitrophes = new HashMap<>();
        try{
            BufferedReader limitrophes = new BufferedReader(new FileReader(this.adresseDptsLimitrophes));
            limitrophes.readLine();
            while ((ligne = limitrophes.readLine()) != null) {
                donneesLigne = ligne.split(";");
                List<String> voisins = new ArrayList<>();
                for (int i = 1; i < donneesLigne.length; i++) {
                    ligne = donneesLigne[i];
                    if (!ligne.equals("") && !ligne.equals("0")) {
                        voisins.add(donneesLigne[i]);
                    }
                }
                dptsLimitrophes.put(donneesLigne[0],voisins);
            }
            limitrophes.close();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public List<String> getLimitrophes(String dpt){
        return dptsLimitrophes.get(dpt);
    }

    public Thread pretraitementShapes(){
                
    	Thread t = new MonitoredThread("Préparation shapes (départements, zonage)") {
            @Override
            public void runTask() {
                Shapefiles.buffer(5, adresseShapesDptsMetro, nameShapeDpts, cheminReseau+"DptsEtendus");
                for (String shapeDOM : shapesDOM){
                    Shapefiles.buffer(5, shapeDOM, nameShapeDpts, cheminReseau+"DptsEtendus");
                }
            }
        };
        t.start();
        return t;
    }

    public Thread pretraitementReseauCuivre(boolean lireSR){
    	Thread t = new MonitoredThread("Préparation des fichiers du réseau cuivre") {

            @Override
            public void runTask() {
                String dossierLPParDpt = filtreDpts(adresseLP);
                //String dossierLPParDpt = "E:/Modeles/modele_BLOM/inputFiles/LP";
                prepareNRA(adresseNRA, cheminReseau+"NRA");
                if (lireSR) prepareSR(adresseSR, cheminReseau+"SR"); // la preparation des fichiers liés aux SR n'a lieu que si la case "utiliser les SR" est cochée sur l'interface (premiere case du module A)
                createPC(dossierLPParDpt, cheminReseau+"PC1");
                Shapefiles.zonagePC(cheminReseau+"DptsEtendus-5km", fichiersZonage[2], fichiersZonage[0], fichiersZonage[1], cheminReseau+"PC1", cheminReseau+"PC2", dptsLimitrophes);
                compteLignesCuivre(cheminReseau+"PC2", cheminReseau+"acces-cuivre.csv");
                System.out.println("Fin du prétraitement des fichiers cuivre");
                // attention zonagePC est long (>3h)
            }
        };

        t.start();
        return t;
    }
    
    public Thread pretraitementCollecte(){
    	Thread t = new MonitoredThread("Pré-traitement réseau de collecte") {

            @Override
            public void runTask() {
                System.out.println("Début du prétraitement de la collecte");
                separeCollecte(adresseCollecte, cheminReseau+"Collecte");
                System.out.println("Fin du prétraitement de la collecte");
            }
        };

        t.start();
        return t;
    }
    
    public Thread createZAPCSRNRA(boolean lireSR, boolean suppression){
    	Thread t = new MonitoredThread("Création des shapefiles des ZANRA") {
            
            @Override
            public void runTask() {
                System.out.println("Début de la création des ZANRA");
                File f;
                if (suppression){
                    f = new File(cheminReseau+"VoronoiPC");
                    if (f.exists()) delete(f);
                }
                Shapefiles.createVoronoiPC(cheminReseau,"DptsEtendus-5km", "NRA", "SR", "PC2", "VoronoiPC", lireSR);
                if (lireSR){
                    if (suppression){
                        f = new File(cheminReseau+"ZASR");
                        if (f.exists()) delete(f);
                    }
                    Shapefiles.fusionShapes(cheminReseau, "VoronoiPC", "ZASR", 2, true, "");
                    if (suppression){
                        f = new File(cheminReseau+"ZANRA");
                        if (f.exists()) delete(f);
                    }
                    Shapefiles.fusionShapes(cheminReseau, "ZASR", "ZANRA", 2, true, "");
                } else{
                    if (suppression){
                        f = new File(cheminReseau+"ZANRA");
                        if (f.exists()) delete(f);
                    }
                    Shapefiles.fusionShapes(cheminReseau, "VoronoiPC", "ZANRA", 3, true, "");
                }
                System.out.println("Fin de la création des ZANRA");
            }
        };

        t.start();
        return t; 
    }
    
    public Thread regrouperNRANRO(List<String> listeDpts, int nbLignesMinNRO, double distMaxNRONRA, boolean lireSR){
        
        long start = System.currentTimeMillis();
        System.out.println("Début du regroupement des NRA en NRO");
        int dist = (int) Math.round(distMaxNRONRA);
        double distmaxNRAPC = 10000; // parametre de filtrage des PC trop éloignés de leurs NRA dans la base des lignes principales
        String nro = "NRO-"+String.valueOf(dist)+"km";
        String adresseNRO = cheminReseau+nro;
        File dirZANRO = new File(cheminReseau+"ZA"+nro);
        File dirNRO = new File(adresseNRO);
        dirNRO.mkdirs();
        File dirTestCollecte = new File(cheminReseau+"TestCollecte");
        dirTestCollecte.mkdirs();
        Thread t = new MonitoredThread("Regroupement des NRA en NRO et création des shapefiles des ZANRO") {

            @Override
            public void runTask() {
                try{
                    // Création des fichiers d'analyse du réseau
                    PrintWriter cuivre = new PrintWriter(cheminReseau+"EtatInitialCuivre.csv");
                    cuivre.println(enteteAnalyse(true, lireSR));
                    PrintWriter cuivre3 = new PrintWriter(cheminReseau+"EtatCuivre_10km.csv");
                    cuivre3.println(enteteAnalyse(true, lireSR));
                    PrintWriter NRONRA = new PrintWriter(adresseNRO+"/Analyse"+nro+".csv");
                    NRONRA.println(enteteAnalyse(false, lireSR));
                    
                    for (String dpt : listeDpts) { // creation du reseau modelise
                        System.out.println("Initialisation pour le département n°"+dpt);
                        ReseauCuivre reseauC = new ReseauCuivre(cheminReseau, dpt, nbLignesMinNRO, 1000*distMaxNRONRA); // conversion du seuil de distance de regroupement de kilomètres en mètres
                        reseauC.loadNRA(cheminReseau+"NRA");
                        if (lireSR) reseauC.loadSR(cheminReseau+"SR"); // le regroupement peut se faire de facon optionnel à l'aide des SR
                        reseauC.loadPC(cheminReseau+"PC2", false, null, lireSR);
                        for (String s : reseauC.etat()){
                            cuivre.println(s);
                        }
                        reseauC.checkPC(distmaxNRAPC); // on supprime les PC situés à une distance trop importante de leurs NRA
                        for (String s : reseauC.etat()){
                            cuivre3.println(s);
                        }
                        reseauC.regrouperNRACollecte(adresseNRO, "Collecte"); // lecture du fichier des liens de collecte puis regroupement des NRA en NRO du reseau modelise
                        for (String s : reseauC.getAnalyseNRO()){ //création du fichier de synthese d'analyse des NRO
                            NRONRA.println(dpt+";"+s);
                        }
                        File zanro = new File(dirZANRO+"/"+dirZANRO.getName()+"_"+dpt);
                        if (zanro.exists()){
                            delete(zanro);
                        }
                    }
                    NRONRA.close();
                    cuivre.close();
                    cuivre3.close();
                } catch (Exception e){
                    e.printStackTrace();
                }
                System.out.println("Fin du regroupement - calcul réalisé en " + Math.round((System.currentTimeMillis() - start) / 1000) + " secondes");
                Shapefiles.fusionShapes(cheminReseau, "ZANRA", dirZANRO.getName(), 1, false, nro);
            }
        };
        
        try{
            PrintWriter writer = new PrintWriter(dirNRO+"/parametres.txt");
            writer.println("Paramètres utilisés pour produire les résultats du présent dossier "+dirNRO.getName());
            writer.println();
            writer.print("Utilisation des sous-répartiteurs du réseau cuivre d'Orange : ");
            if (lireSR) writer.println("OUI");
            else writer.println("NON");
            writer.println("Nombre minimal théorique de lignes desservies par un NRO : "+nbLignesMinNRO+" lignes");
            writer.println("Distance maximale entre NRA regroupé et NRA regroupeur autorisant à passer sous le seuil précédant : "+distMaxNRONRA+ " km");
            writer.close();
        } catch(FileNotFoundException e){
            e.printStackTrace();
        }
        t.start();
        return t;
    }
    
    private String enteteAnalyse(boolean NRA, boolean lireSR){
        String entete = "Departement;Zone";
        if (NRA)
            entete+=";NRA";
        else
            entete+=";NRO;Nombre de NRA";
        if(lireSR)
            entete+=";Nombre de SR";
        entete+=";Nombre de PC;Nombre de lignes";
        return entete;
    }
    
    public Thread traceReseau(boolean limitrophes, boolean gc, boolean routes, boolean tracerShpReseau, String nro, List<String> dptChoisis, HashMap<String, Double> parametresReseau){
    	Thread t = new MonitoredThread("Module topo et sortie des fichiers intermédiaires \"BLO\"") {

            @Override
            public void runTask() {
                Shapefiles.preparerGraphes(dptChoisis, dptsLimitrophes, cheminReseau, "ZA"+nro, nro, "BLO", nro.replace("NRO", ""), limitrophes, gc, routes, tracerShpReseau, parametresReseau);
            }
        };

        t.start();
        return t;
    }
    
    // méthodes pour le module A
    
    protected String filtreDpts(String nomFichier) {
        File f = new File(nomFichier);
        System.out.println("Filtrage par département sur : " + nomFichier);
        
        File dir = new File(f.getParent()+"/LP");
        dir.mkdir();
        String line, dpt;
        int n=0;
        HashMap<String,PrintWriter> writers = new HashMap<>();
        long start = System.currentTimeMillis();
        
        try {
            
            BufferedReader br = new BufferedReader(new FileReader(f));
            String entete = br.readLine();
            
            while ((line = br.readLine()) != null) {
                dpt = dpt(line.replace("\"","").split(";")[6]);
                if (!writers.containsKey(dpt)){
                    PrintWriter writer = new PrintWriter(dir+"/LP_"+dpt+".csv");
                    writer.println(entete);
                    writer.println(line);
                    writers.put(dpt,writer);
                    System.out.println(writers.size()+" départements différents trouvés");
                } else writers.get(dpt).println(line);
                n++;
                if(n%10000 == 0){
                    System.out.print(n / 1000 + " k | ");
                }
            }
            br.close();
            for (PrintWriter writer : writers.values()){
                writer.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Temps pour filtrer par département : "+(System.currentTimeMillis()-start)+" milisecondes");
        return dir.getPath();
    }
    
    
    protected void createPC(String origine, String destination) {
        String line;
        long lignesExclues = 0;
        
        try {
            File dir = new File(origine);
            File newDir = new File(destination);
            newDir.mkdirs();
            String codePCSR;
            String fields[];
            int pos_codenra = 4;
            int pos_codesr = 5;
            int pos_codepc = 6;
            int pos_codecommunepc = 7;
            int pos_coordxpc = 10;
            int pos_coordypc = 11;
            int pos_scrcoord = 12;
            for (File file : dir.listFiles()){
                //lecture
                long start = System.currentTimeMillis();
                int n=0;
                BufferedReader br = new BufferedReader(new FileReader(file));
                HashMap<String,PCBasic> data = new HashMap<>();
                System.out.println("Lecture du fichier " + file + " | en-tête :");
                System.out.println(br.readLine());

                // creation de la base des PC à partir de la base des lignes principales
                while ((line = br.readLine()) != null) {
                    fields = line.replace("\"","").split(";");
                    if (fields.length >= 13){ 
                        codePCSR = fields[pos_codesr] + "-" + fields[pos_codepc]; // la concatenation de la clé SR (6e colonne) et de la clé PC (7e colonne) permet d'identifier un PC dans la base des lignes cuivre (la clé PC seule n'est pas suffisante)
                        if (!data.containsKey(codePCSR)){
                            data.put(codePCSR, new PCBasic(codePCSR + ";" + fields[pos_codenra]+";"+fields[pos_codesr]+";"+fields[pos_codepc]+";"+fields[pos_codecommunepc]+";"+fields[pos_coordxpc]+";"+fields[pos_coordypc]+";"+fields[pos_scrcoord]));
                        } else data.get(codePCSR).addLigne();
                    } else{
                        lignesExclues++;
                    }
                    n++;
                    if(n%10000 == 0){ // suivi de l'avancee de la creation de la base des PC
                        System.out.print(n / 1000 + " k | ");
                    }
                }
                br.close();

                // écriture
                String dpt = file.getName().replace("LP", "");
                PrintWriter writer = new PrintWriter(newDir+"/"+newDir.getName()+dpt);
                writer.println("Identifiant;NRA;SR;PC;Commune;X;Y;Referentiel;Lignes");
                for (PCBasic value : data.values()){
                    writer.println(value.print());
                }
                writer.close();
                System.out.println("ModuleTopo.CreatePC() fini pour le département "+dpt.replace(".csv", "").replace("_", "")+" ; temps nécessaire : "+(System.currentTimeMillis()-start)+" milisecondes");
            }
            System.out.println("Nombres de lignes exclues : "+lignesExclues);
        }
        catch (Exception e) {
            e.printStackTrace();
        } 
    }
    
    private class PCBasic {
        private final String infos;
        private int nbLignes;

        PCBasic(String infos){
            this.infos = infos;
            nbLignes = 1;
        }

        void addLigne(){
            nbLignes++;
        }
        
        String print(){
            return infos+";"+nbLignes;
        }

    }
    
    protected void compteLignesCuivre(String origine, String destination){ // Comptage des lignes principales du reseau cuivre
        try{
            PrintWriter writer = new PrintWriter(destination);
            writer.println("Departement;ZTD;ZMD_AMII;ZMD_RIP");
            String line;
            String[] fields;
            int compteurZTD, compteurAMII, compteurRIP, nbLignes;
            File dir = new File(origine);
            for (File f : dir.listFiles()){
                String dpt = f.getName().replace(dir.getName()+"_", "").replace(".csv", "");
                System.out.println("Comptage des lignes principales du réseau cuivre pour le département "+dpt);
                BufferedReader reader = new BufferedReader(new FileReader(f));
                reader.readLine();
                compteurZTD = 0;
                compteurAMII = 0;
                compteurRIP = 0;
                while ((line = reader.readLine())!=null){ // comptage des lignes par zone de régulation
                    fields = line.split(";");
                    nbLignes = Integer.parseInt(fields[7]);
                    switch(fields[8]){
                        case "ZMD_RIP":
                            compteurRIP+=nbLignes;
                            break;
                        case "ZMD_AMII":
                            compteurAMII+=nbLignes;
                            break;
                        default:
                            compteurZTD += nbLignes;
                            break;
                    }
                }
                reader.close();
                writer.println(dpt+";"+compteurZTD+";"+compteurAMII+";"+compteurRIP);
            }
            writer.close();
        } catch(FileNotFoundException e){
            System.out.println("Problème avec "+origine+" ou "+destination);
            e.printStackTrace();
        } catch(IOException e){
            e.printStackTrace();
        }
        
    }

    protected void prepareNRA(String origine, String destination){ 
        Map<String, List<String>> dptNRA = litEtSepare(origine);
        File dir = new File(destination);
        dir.mkdirs();
        String nra;
        String[] fields;
        int pos_codenra = 0;
        int pos_coordx = 8;
        int pos_coordy = 9;
        int pos_epsg_scr = 10;
        for (String dpt : dptNRA.keySet()){
            try{
                PrintWriter writer = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+".csv");
                writer.println("NRA;X;Y;Referentiel");
                for (String line : dptNRA.get(dpt)){
                    fields = line.split(";");
                    nra = fields[pos_codenra]; // la clé NRA doit se trouver dans la premiere colonne du fichier listant les NRA
                    if (nra.endsWith("E00")) // correction le cas echeant des codes NRA formates en notation scientifique
                        nra = nra.substring(0, 5) + "EOO";
                    writer.println(nra+";"+fields[pos_coordx]+";"+fields[pos_coordy]+";"+fields[pos_epsg_scr]); // on conserve seulement les coordonnées X-Y du NRA ainsi que le systeme de projection associé à ces coordonnées
                }
                writer.close();
            } catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }
    }
    
    private void prepareSR(String origine, String destination){
            Map<String, List<String>> dptSR = litEtSepare(origine);
            File dir = new File(destination);
            dir.mkdirs();
            String nra;
            String[] fields;
            int pos_codesr = 0;
            int pos_codenra = 1;
            int pos_coordx = 6;
            int pos_coordy = 7;
            int pos_epsg_scr = 8;
            for (String dpt : dptSR.keySet()){
                try{
                    PrintWriter writer = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+".csv");
                    writer.println("SR;NRA;X;Y;Referentiel");
                    for (String line : dptSR.get(dpt)){
                        fields = line.split(";");
                        nra = fields[pos_codenra];
                        if (nra.endsWith("E00"))
                            nra = nra.substring(0, 5) + "EOO";
                        writer.println(fields[pos_codesr]+";"+nra+";"+fields[pos_coordx]+";"+fields[pos_coordy]+";"+fields[pos_epsg_scr]);
                    }
                    writer.close();
                } catch(FileNotFoundException e){
                    e.printStackTrace();
                }
            }
    }
    
    private String dpt(String codeNRA){
        if(codeNRA.substring(0, 2).equals("97"))
            return codeNRA.substring(0, 3);
        else return codeNRA.substring(0, 2);
    }
    
    private Map<String, List<String>> litEtSepare(String fichier){
        Map<String, List<String>> res = new HashMap<>();
        String line, dpt;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(fichier));
            reader.readLine();
            while ((line = reader.readLine()) != null){
                dpt = line.substring(0, 2);
                if (dpt.equals("97")) dpt = line.substring(0, 3); // outremer
                if (!res.containsKey(dpt))
                    res.put(dpt, new ArrayList<String>());
                res.get(dpt).add(line);
            }
            reader.close();
        } catch (IOException e){
            System.out.println("Problème avec le fichier "+fichier);
            e.printStackTrace();
        }
        return res;
    }
    
    protected void separeCollecte(String origine, String destination){
        Map<String, List<String>> res = new HashMap<>();
        String line, dpt1, dpt2, header, codeNRA1, codeNRA2;
        String[] fields;
        int pos_codenra1 = 1;
        int pos_codenra2 = 2;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(origine));
            header = reader.readLine();
            while ((line = reader.readLine()) != null){
                fields = line.split(";");
                codeNRA1 = fields[pos_codenra1];
                // attention aux codes NRA qui ont l'air de nombres en notation scientifique (vérifier aussi le fichier d'entrée sous Notepad++)
                if (codeNRA1.substring(5,7).equals("E0"))
                    codeNRA1 = codeNRA1.substring(0, 5) + "EOO";
                dpt1 = codeNRA1.substring(0,2);
                if (dpt1.equals("97")) dpt1 = codeNRA1.substring(0, 3); // outremer
                codeNRA2 = fields[pos_codenra2];
                if (codeNRA2.substring(5,7).equals("E0"))
                    codeNRA2 = codeNRA2.substring(0, 5) + "EOO";
                dpt2 = codeNRA2.substring(0,2);
                if (dpt2.equals("97")) dpt2 = codeNRA2.substring(0, 3); // outremer
                if (!res.containsKey(dpt1))
                    res.put(dpt1, new ArrayList<>());
                res.get(dpt1).add(line);
                if (!dpt2.equals(dpt1)){
                    if (!res.containsKey(dpt2))
                        res.put(dpt2, new ArrayList<>());
                    res.get(dpt2).add(line);
                }
            }
            reader.close();

            for (String dpt : res.keySet()){
                File dir = new File(destination);
                dir.mkdirs();
                System.out.println("Impression du fichier des liens de collecte pour le département "+dpt);
                PrintWriter liens = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+".csv", "utf-8");
                liens.println(header);
                for (String s : res.get(dpt)){
                    liens.println(s);
                }
                liens.close();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    
    private void delete(File f){
       if (f.isDirectory()){
            for (File child : f.listFiles()){
                delete(child);
            }
       }
       f.delete();
    }
    
}
