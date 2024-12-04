/*
 * Copyright (c) 2024, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
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

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import arcep.ftth2.configuration.Configuration;

public class Parametres {
    
    public Parametres(){}
    
    ///// Paramètres globaux
    private final DateFormat dateFormat = new SimpleDateFormat("YYYYMMdd_HH_mm");
    Set<String> zones;
    private final String cheminReseau = Configuration.get().cheminReseau;

    public void setZones(boolean ztd, boolean amii, boolean rip){
        zones = new HashSet<>();
        if (ztd) zones.add("ZTD");
        if (amii) zones.add("ZMD_AMII");
        if (rip) zones.add("ZMD_RIP");
    }
    
    // deux champs définis par setDossierResultats
    String racineResultats;
    String terminaison; 
    
    public void setDossierResultats(String racineResultats, boolean ztd, boolean amii, boolean rip, String trace, String prefix){
        Date date = new Date();
        terminaison = dateFormat.format(date);
        if (ztd){
            if(amii){
                if(rip) terminaison += "_global";
                else terminaison += "_prive";
            } else{
                terminaison += "_ZTD";
                if (rip) terminaison += "_RIP";
            }
        } else{
            if (amii){
                if (rip) terminaison += "_ZMD";
                else terminaison += "_AMII";
            } else{
                if (rip) terminaison += "_RIP";
                else terminaison += "_ListeCommunes";
            }
        }
        terminaison += trace.replace("BLO", "");
        this.racineResultats = racineResultats +"/" + (prefix == null ? "" : (prefix + "/"))  + terminaison;
        File dir = new File(this.racineResultats);
        dir.mkdirs();
    }
    
    public void setDossierResultats(String racineResultats, boolean ztd, boolean amii, boolean rip, String trace) {
    	setDossierResultats(racineResultats, ztd, amii, rip, trace, null);
    }
    
    private Map<String,Set<String>> listeCommunes = new HashMap<>();  

    public void setListeCommunes(String dossierCommunes, boolean listeSpeciale, String fichierListe, List<String> listeDpts){
        listeCommunes = new HashMap<>();
        try {
            //BufferedReader ficCommunes;
            String ligne, codeINSEE, donneesLigne[];
            if(listeSpeciale){
                String dpt;
                try(BufferedReader ficCommunes = new BufferedReader(new FileReader(dossierCommunes+"/"+fichierListe))) {
                    ficCommunes.readLine();
                    while ((ligne = ficCommunes.readLine()) != null) {
                        donneesLigne = ligne.split(";");
                        codeINSEE = donneesLigne[0];
                        if (codeINSEE.length() == 4)
                            codeINSEE = "0".concat(codeINSEE);
                        dpt = codeINSEE.substring(0, 2);
                        if (listeCommunes.containsKey(dpt))
                            listeCommunes.put(dpt, new HashSet<>());
                        listeCommunes.get(dpt).add(codeINSEE);
                    }
                }
                
            }
            else{
                String zone;
                File dir = new File(dossierCommunes);
                for (String dpt : listeDpts){
                    listeCommunes.put(dpt, new HashSet<>());
                    try(BufferedReader ficCommunes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+dpt+".csv"))) {
                        

                        ficCommunes.readLine();
                        while ((ligne = ficCommunes.readLine()) != null) {
                            donneesLigne = ligne.split(";");
                            codeINSEE = donneesLigne[0];
                            if (codeINSEE.length() == 4)
                                codeINSEE = "0".concat(codeINSEE);
                            zone = donneesLigne[1];
                            if (!zone.equals("ZTD"))
                                zone = "ZMD_"+zone;
                            if (this.zones.contains(zone)) {
                                listeCommunes.get(dpt).add(codeINSEE);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    boolean ficUnitesOeuvre, ficNRO, ficPM, ficPBO;
    boolean ficLineaires, ficCouts, ficLongueurs;
    
    public void setSorties(boolean ficUnitesOeuvre, boolean ficNRO, boolean ficPM, boolean ficPBO, boolean ficLineaires, boolean ficCouts, boolean ficLongueurs){
        this.ficUnitesOeuvre = ficUnitesOeuvre;
        this.ficNRO = ficNRO;
        this.ficPM = ficPM;
        this.ficPBO = ficPBO;
        this.ficLineaires = ficLineaires;
        this.ficCouts = ficCouts;
        this.ficLongueurs = ficLongueurs;
    }  
    
    ///// Paramètres réseau
    
    public boolean NROauxNRA = false;
    private int seuilPMint = 12;
    private int seuilPM_ZMD = 300;
    private int seuilPM_ZTD_BD = 300;
    private int seuilPM_ZTD_HD = 100;
    boolean utiliseMediane = false;
    double maxDistPMPBO = 5000;
    double distMinPM = 50; // en mètres
    
    /**
     *
     * @param seuilPM_ZMD
     * @param seuilPM_ZTD_BD
     * @param maxDistPMPBO
     * @param seuilPM_ZTD_HD
     * @param seuilPMint
     */
    public void setPM(int seuilPM_ZMD, int seuilPM_ZTD_BD, boolean utiliseMediane, double maxDistPMPBO, int seuilPM_ZTD_HD, int seuilPMint){
        this.utiliseMediane = utiliseMediane;
        this.maxDistPMPBO = maxDistPMPBO; // en mètres
        this.seuilPM_ZMD = seuilPM_ZMD;
        this.seuilPM_ZTD_BD = seuilPM_ZTD_BD;
        this.seuilPM_ZTD_HD = seuilPM_ZTD_HD;
        this.seuilPMint = seuilPMint;
    }
    
    private double tauxCouplage;
    int nbMinFibreCollectePM300;
    int nbMinFibreCollectePM100;
    
    /**
     *
     * @param tauxCouplage
     * @param nbMinFibreCollectePM300
     * @param nbMinFibreCollectePM100
     */
    public void setTransportOptique(double tauxCouplage, int nbMinFibreCollectePM300, int nbMinFibreCollectePM100){
        this.tauxCouplage = tauxCouplage;
        this.nbMinFibreCollectePM300 = nbMinFibreCollectePM300;
        this.nbMinFibreCollectePM100 = nbMinFibreCollectePM100;
        
    }
    
    private Map<String,Map<String,Integer>> demande;
    
    public void initDemande(List<String> dptsChoisis){
        demande = new HashMap<>();
        String[] zonesMacro = new String[]{"ZTD", "ZMD_AMII", "ZMD_RIP"};
        for (String dpt : dptsChoisis){
            Map<String, Integer> demandeCible = new HashMap<>();
            for (String zone : zonesMacro){
                demandeCible.put(zone, 0);
            }
            demande.put(dpt, demandeCible);
        }
    }
    
    public void addDemande(String fichier){
        String line, dpt;
        String[] fields;
        String[] zonesMacro = new String[]{"ZTD", "ZMD_AMII", "ZMD_RIP"};
        try{
            BufferedReader reader = new BufferedReader(new FileReader(fichier));
            reader.readLine();
            while((line = reader.readLine())!=null){
                fields = line.split(";");
                dpt = fields[0];
                if (dpt.length() == 1)
                    dpt = "0"+dpt;
                if (demande.containsKey(dpt)){
                    for (int i = 0;i<3;i++){
                            demande.get(dpt).put(zonesMacro[i],demande.get(dpt).get(zonesMacro[i])+Integer.parseInt(fields[i+1]));
                    }
                }
            }
            reader.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    ///// Paramètres de dimensionnement des unités d'oeuvre
    
    int nbMaxLignesPBO;
    
    private int nbMaxLignesPM_ZMD;
    private int nbMaxLignesPM_ZTD_BD;
    private int nbMaxLignesPM_ZTD_HD;
    private int nbMaxLignesPM_ZTD_HD_int;

    public void setNbMaxLignesPM(int nbMaxLignesPM_ZMD, int nbMaxLignesPM_ZTD_BD, int nbMaxLignesPM_ZTD_HD, int nbMaxLignesPM_ZTD_HD_int){
        this.nbMaxLignesPM_ZMD = nbMaxLignesPM_ZMD;
        this.nbMaxLignesPM_ZTD_BD = nbMaxLignesPM_ZTD_BD; 
        this.nbMaxLignesPM_ZTD_HD = nbMaxLignesPM_ZTD_HD; 
        this.nbMaxLignesPM_ZTD_HD_int = nbMaxLignesPM_ZTD_HD_int;
    }
    
    public void setNbMaxLignesPBO(int nbMaxLignesPBO) {
		this.nbMaxLignesPBO = nbMaxLignesPBO;
	}

	public int getNbMaxLignesPM(String zone){
        switch(zone){
            case "ZMD":
                return nbMaxLignesPM_ZMD;
            case "ZTD_BD":
                return nbMaxLignesPM_ZTD_BD; 
            case "ZTD_HD":
                return nbMaxLignesPM_ZTD_HD; 
            case "ZTD_HD_int":
                return nbMaxLignesPM_ZTD_HD_int;
            default:
                return -1;
        }
    }
    
    double facteurSurcapaciteDistri;
    double facteurSurcapaciteTransport;
    double longFibreSupp;
    double seuil_boitier;

    public void setUO(double facteurSurcapaciteDistri, double facteurSucaocaciteTransport, double longFibreSupp, double seuil_boitier){
       this.facteurSurcapaciteDistri = facteurSurcapaciteDistri;
       this.facteurSurcapaciteTransport = facteurSucaocaciteTransport;
       this.longFibreSupp = longFibreSupp;
       this.seuil_boitier = seuil_boitier;
    }
    
    private int nbLignesParCoupleurPMExt;
    private int nbLignesParCoupleurPMint;
    private int nbLignesParCoupleurNRO;
    
    private int nbCoupleursParTiroirsPMExt;
    private int nbCoupleursParTiroirsPMint;
    private int nbCoupleursParTiroirsNRO;
    
    public int facteurDuplicationPresenceOC;
    
    public void setCoupleurs(){
        this.nbLignesParCoupleurPMExt = 32;
        this.nbLignesParCoupleurPMint = 8; 
        this.nbLignesParCoupleurNRO =2; // coupleurs 2*2 

        this.nbCoupleursParTiroirsPMExt = 4;
        this.nbCoupleursParTiroirsPMint = 4;
        this.nbCoupleursParTiroirsNRO = 36; // 144 connecteurs dans un tiroir 4U - un coupleur 2x2 occupe 4 connecteurs

        this.facteurDuplicationPresenceOC = 2;
    }

    public final int[] calibre = {6,12,24,48,72,96,144,288,576,720}; // tous les calibres possibles
    private final double[] diametresAerien = {6.1,6.1,8.3,9.4,10.7,11.3,11.3,14.6}; // en mm
    private final double[] diametresSouterrain = {6.1,6.1,8.4,8.4,10.2,12,12,13.2,18,18.5}; // en mm
    private int indiceCalibreMaxCablesSouterrain;
    private int indiceCalibreMaxCablesAerien;
    private int indiceCalibreMinHorizontal;

    public void setCalibresLimites(int tailleMaxSouterrain, int tailleMaxAerien, int tailleMinHorizontal){
        for (int i = 0; i<calibre.length;i++){
            if (tailleMaxSouterrain == calibre[i]){
                this.indiceCalibreMaxCablesSouterrain = i;
                System.out.println("indice max souterrain : "+i);
            }
            if (tailleMaxAerien == calibre[i]){
                this.indiceCalibreMaxCablesAerien = i;
                System.out.println("indice max aérien : "+i);
            }
            if (tailleMinHorizontal == calibre[i]){
                this.indiceCalibreMinHorizontal = i;
                System.out.println("indice min horizontal : "+i);
            }
        }
    }
    
    double partReconstrPTAerien;

    public void setGC(double partReconstrPTAerien){
        this.partReconstrPTAerien = partReconstrPTAerien;
    }
    
    String modeReconstrGCTransport;
    String modeReconstrGCDistrib;
    
    public void setModeReconstructionGC(String modeReconstrGCTransport, String modeReconstrGCDistrib){
        this.modeReconstrGCTransport = modeReconstrGCTransport;
        this.modeReconstrGCDistrib = modeReconstrGCDistrib;
    }
    
    int nbFibresParTiroir;
    int nbTiroirsParRTO;
    double surfaceRTO ;
    double facteurSurface;

    public void setDimensionnementNRO(int nbFibresParTiroir, int nbTiroirsParRTO, double surfaceBaie, double facteurGlobal){
        this.nbFibresParTiroir = nbFibresParTiroir;
        this.nbTiroirsParRTO = nbTiroirsParRTO;
        this.surfaceRTO = surfaceBaie;
        this.facteurSurface = facteurGlobal;
    }
    
    ////// Fonctions d'utilisation des paramètres ////////////////
    
    /// généraux
    public Set<String> getCommunes(String dpt){
        return listeCommunes.get(dpt);
    }

    public static double arrondir(double valeur, int nbChiffresVirgule) {
        return ((double) Math.round(valeur * Math.pow(10, nbChiffresVirgule))) / Math.pow(10, nbChiffresVirgule);
    }
    
    /// réseau
    
    public int seuilPM(String zone){
        switch(zone){
            case "int":
                return seuilPMint;
            case "ZTD_HD":
                return seuilPM_ZTD_HD;
            case "ZTD_BD":
                return seuilPM_ZTD_BD;
            default:
                return seuilPM_ZMD;
        }
    }

    public double tauxCouplage(String zone){
        return tauxCouplage;
    }

    public int nbFibresMin(String zone){
        switch(zone){
            case "ZMD":
            case "ZTD_BD":
                return nbMinFibreCollectePM300;
            default:
                return nbMinFibreCollectePM100;
        }
    }

    public Map<String,Integer> getDemande(String dpt){
        return demande.get(dpt);
    }
    
    public int getCalibreMax(int modePoseSortie){
        if (modePoseSortie <= 1) return this.indiceCalibreMaxCablesAerien;
        else return this.indiceCalibreMaxCablesSouterrain;
    }

    public int getCalibreMin(){
        return this.indiceCalibreMinHorizontal;
    }

    public double[] getDiametres(int modePoseSortie){
        switch(modePoseSortie){
            case 0:
            case 1:
                return this.diametresAerien;
            default:
                return this.diametresSouterrain;
        }
    }
    
    public int getLignesParCoupleurs(String type){
        switch(type){
            case "PMext":
                return this.nbLignesParCoupleurPMExt;
            case "PMint":
                return this.nbLignesParCoupleurPMint;
            case "NRO":
                return nbLignesParCoupleurNRO;
            default:
                return 0;         
        }
    }
    
        public int getCoupleursParTiroirs(String type){
        switch(type){
            case "PMext":
                return this.nbCoupleursParTiroirsPMExt;
            case "PMint":
                return this.nbCoupleursParTiroirsPMint;
            case "NRO":
                return nbCoupleursParTiroirsNRO;
            default:
                return 0;          
        }
    }

    public void zipShp(String nomFichier) {

        int BUFFER = 2048;

        BufferedInputStream origin;
        String nomGenerique = nomFichier.replace(".shp", "");
        String fichiers[] = new String[4];
        fichiers[0] = nomFichier;
        fichiers[1] = nomGenerique + ".dbf";
        fichiers[2] = nomGenerique + ".shx";
        fichiers[3] = nomGenerique + ".prj";

        byte data[] = new byte[BUFFER];

        try {

            FileOutputStream dest = new FileOutputStream(nomGenerique + ".zip");
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

            for (int i = 0; i < 4; i++) {

                FileInputStream fi = new FileInputStream(fichiers[i]);
                origin = new BufferedInputStream(fi, BUFFER);
                ZipEntry entry = new ZipEntry((new File(fichiers[i])).getName());
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
            out.close();

        } catch (Exception e) {
        }
    }

    public void print(String traceSelectionne, String[] fichiersDemande){
        String s;
        try{
            PrintWriter writer = new PrintWriter(this.racineResultats+"/parametres_"+this.terminaison+".txt");
            writer.println("Paramètres utilisés pour produire les résultats du présent dossier "+this.terminaison);
            writer.println();
            writer.println();
            writer.println("1. Paramètres du module topologique");
            writer.println();
            BufferedReader reader = new BufferedReader(new FileReader(this.cheminReseau+traceSelectionne+"/parametres.txt"));
            reader.readLine();
            reader.readLine();
            while ((s = reader.readLine()) != null){
                writer.println(s);
            }
            reader.close();
            writer.println();
            writer.println("2. Fichiers utilisés pour la déterminer la demande cible du réseau");
            writer.println();
            writer.println("La demande cible a été initialisée à partir de "+fichiersDemande.length+" fichiers, listés ci-dessous :");
            for (String fichier : fichiersDemande){
                writer.println("   - "+fichier);
            }
            writer.println();
            writer.println("3. Paramètres généraux de dimensionnement du réseau");
            writer.println();
            writer.println("Seuil pour les PM intérieurs en ZTD_HD : "+this.seuilPMint+" lignes");
            writer.println("Seuil pour les PM extérieurs en ZTD_HD : "+this.seuilPM_ZTD_HD+" lignes");
            writer.println("Seuil pour les PM extérieurs en ZTD_BD : "+this.seuilPM_ZTD_BD+" lignes");
            writer.println("Seuil pour les PM extérieurs en ZMD    : "+this.seuilPM_ZMD+" lignes");
            if (utiliseMediane) writer.print("Médiane");
            else writer.print("Moyenne");
            writer.println(" maximale pour les distances PM ext - PBO : "+this.maxDistPMPBO+" mètres");
            writer.println();
            writer.println("Ratio minimum entre le nombre de fibres en transport et en distribution au niveau d'un PM : "+this.tauxCouplage);
            writer.println("Nombre minimum de fibres en amont d'un PM300 : "+this.nbMinFibreCollectePM300);
            writer.println("Nombre minimum de fibres en amont d'un PM100 : "+this.nbMinFibreCollectePM100);
            writer.println();
            writer.println("4. Dimensionnement des câbles et boîtiers d'épissurage");
            writer.println();
            //writer.println("Part de GC pleine terre reconstruit en aérien                              : "+this.partReconstrPTAerien);
            writer.println("Mode de reconstruction du GC portant du transport               : " + this.modeReconstrGCTransport);
            writer.println("Mode de reconstruction du GC ne portant que de la distribution  : " + this.modeReconstrGCDistrib);
            writer.println("Pas de paramètre exogène de reconstruction de la conduite ou de la pleine terre");
            writer.println("Facteur appliqué aux longueurs réseau pour obtenir les longueurs de câbles : " + this.longFibreSupp);
            writer.println();
            writer.println("Facteur minimal de surcapacité en transport    : "+this.facteurSurcapaciteTransport);
            writer.println("Facteur minimal de surcapacité en distribution : "+this.facteurSurcapaciteDistri);
            writer.println();
            writer.println("Calibre maximal des câbles en souterrain : "+this.calibre[this.getCalibreMax(7)]);
            writer.println("Calibre maximal des câbles en aérien     : "+this.calibre[this.getCalibreMax(0)]);
            writer.println("Calibre minimal des câbles en horizontal : "+this.calibre[this.getCalibreMin()]);
            writer.println();
            writer.println("Calibres disponibles et diamètres des câbles en fonction du calibre et du type :");
            writer.print("Calibre   ");
            int longueurMaxChiffre = 5;
            for (int i : this.calibre){
                s = String.valueOf(i);
                int nbCaracteresManquants = longueurMaxChiffre-s.length();
                for (int j = 1;j<nbCaracteresManquants;j++){
                    s = " "+s;
                }
                writer.print(" | "+s);
            }
            writer.println();
            writer.print("Souterrain");
            for (double d : this.diametresSouterrain){
                s = String.valueOf(d);
                int nbCaracteresManquants = longueurMaxChiffre-s.length();
                for (int j = 1;j<nbCaracteresManquants;j++){
                    s = " "+s;
                }
                writer.print(" | "+s);
            }
            writer.println();
            writer.print("Aérien    ");
            for (double d : this.diametresAerien){
                s = String.valueOf(d);
                int nbCaracteresManquants = longueurMaxChiffre-s.length();
                for (int j = 1;j<nbCaracteresManquants;j++){
                    s = " "+s;
                }
                writer.print(" | "+s);
            }
            writer.println();
            writer.println();
            writer.println("Distance maximale entre deux boîtiers d'épissurage : "+this.seuil_boitier);
            writer.println();
            writer.println("5. Dimensionnement des équipements des noeuds du réseau");
            writer.println();
            writer.println("PBO - nombre maximal de lignes par boîtier : "+this.nbMaxLignesPBO);
            writer.println();
            writer.println("PM300 - nombre maximal de lignes par armoire en ZMD           : "+this.nbMaxLignesPM_ZMD);
            writer.println("PM300 - nombre maximal de lignes par armoire en ZTD_BD        : "+this.nbMaxLignesPM_ZTD_BD);
            writer.println("PM100 - nombre maximal de lignes par armoire en ZTD_HD        : "+this.nbMaxLignesPM_ZTD_HD);
            writer.println("PM intérieur - nombre maximal de lignes par boîtier en ZTD_BD : "+this.nbMaxLignesPM_ZTD_HD_int);
            writer.println();
            writer.println("NRO - nombre maximal de fibres par tiroir optique : "+this.nbFibresParTiroir);
            writer.println("NRO - nombre maximal de tiroir par RTO            : "+this.nbTiroirsParRTO);
            writer.println("NRO - surface d'un RTO                            : "+this.surfaceRTO);
            writer.println("NRO - coefficient multiplicateur de surface       : "+this.facteurSurface);
            writer.close();

        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
}
