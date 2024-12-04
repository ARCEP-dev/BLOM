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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JOptionPane;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import arcep.ftth2.configuration.Configuration;
import arcep.utilitaires.multithread.MultiTask;

public class Deploiement implements Runnable{

    FenProgression fen;
    
    final List<String> listeDpts;
    private int nbDptTraites;
    final Parametres parametres;
    final CoutsUnitaires coutsUnitaires;
    final String cheminReseau;
    final String racineResultats;
    final String dossierNRO;
    final String dossierBLO;
    final String fichierImmeubles;

	private Consumer<Couts> coutsListener;
            
    public Deploiement(Parametres parametres, CoutsUnitaires couts, List<String> listeDpts, String cheminReseau, String cheminResultats, String dossierNRO, String dossierBLO, String fichierImmeubles) {
        this.parametres = parametres;
        this.coutsUnitaires = couts;
        this.listeDpts = listeDpts;
        this.cheminReseau = cheminReseau == null || cheminReseau.length() == 0 ? Configuration.get().cheminReseau : cheminReseau;
        this.racineResultats = cheminResultats == null || cheminResultats.length() == 0 ? parametres.racineResultats : cheminResultats;
        this.dossierNRO = dossierNRO.replace("-old","");
        this.dossierBLO = dossierBLO;
        this.fichierImmeubles = fichierImmeubles;
    }
    
    @Override
    public void run() {
        evaluation();
    }

    private void evaluation() {
        
        if (fen != null) fen.afficher("Initialisation");
        long start = System.currentTimeMillis();

        boolean fichiersTest = false;
                
        System.out.println("Le set parametres.zones :");
        for (String zone : parametres.zones){
            System.out.println(zone);
        }
        
        try {
            
            if (fen != null) fen.initBarre(listeDpts.size());
            nbDptTraites = 0;
            
            ////////////////////////////////////////////////////////
            /// INITIALISATION  DES FICHIERS DE SORTIE GLOBAUX
            ////////////////////////////////////////////////////////
            
            if (fen != null) fen.afficher("Initialisation des fichiers de sortie");
            
            File dir = new File(racineResultats+"/Resultats_00_national");
            dir.mkdirs();
            
            PrintWriter csvUONatDetail = new PrintWriter(dir+"/UONatDetail_"+parametres.terminaison+".csv");
            csvUONatDetail.println("Departement;Zone;"+UO.header(parametres, true, true,2));
            
            PrintWriter csvUONatCouts = new PrintWriter(dir+"/UONatCouts_"+parametres.terminaison+".csv");
            csvUONatCouts.println("Zone;"+UO.header(parametres, true, true,1));
           
            PrintWriter csvUONatparNRO = new PrintWriter(dir+"/UONatparNRO_"+parametres.terminaison+".csv");
            csvUONatparNRO.println("Departement;Zone;NRO;"+UO.header(parametres, true, true,1));
            
            PrintWriter csvCoutsNat = new PrintWriter(dir+"/CoutsNat_"+parametres.terminaison+".csv");
            csvCoutsNat.println("Departement;Zone;"+Couts.header());
            
            PrintWriter csvAnalyseGC = new PrintWriter(dir+"/AnalyseGC_"+parametres.terminaison+".csv");
            csvAnalyseGC.println("Departement;Zone;Aerien existant;PT reconstruit aerien;Souterrain existant;PT reconstruit souterrain;Immeubles");            
            PrintWriter csvAnalyseNROPM = new PrintWriter(dir+"/AnalyseNROPM_"+parametres.terminaison+".csv");
            csvAnalyseNROPM.print("Departement;Zone;NRO;IdPM;Type;Distance NRO-PM;Lignes");
            for (int i = parametres.getCalibreMin();i<=parametres.getCalibreMax(7);i++){
                csvAnalyseNROPM.print(";Nb cables_"+parametres.calibre[i]+" entrants");
            }
            csvAnalyseNROPM.println(";Nb fibres entrantes;Longueur moyenne PM-PBO;Premier decile;Mediane;Dernier decile");
            
            PrintWriter csvAnalyseDemande = new PrintWriter(dir+"/AnalyseDemande_"+parametres.terminaison+".csv");
            csvAnalyseDemande.println("Departement;ZTD;AMII;RIP");
            for (String dpt : listeDpts) {
                csvAnalyseDemande.print(dpt);
                Map<String, Integer> demandeCible = parametres.getDemande(dpt);
                String[] zones = {"ZTD","ZMD_AMII","ZMD_RIP"};
                for (String zone : zones){
                    csvAnalyseDemande.print(";"+demandeCible.get(zone));
                }
                csvAnalyseDemande.println();
            }
            csvAnalyseDemande.close();
            
            boolean coloriage = !this.dossierBLO.contains("-GC");
            
            Map<String, UO> uoZones = new HashMap<>();
            for (String zone : parametres.zones){
                uoZones.put(zone, new UO(zone, true, true, parametres));
            }
            
            ////////////////////////////////////////////////////////
            /// BOUCLE SUR LES DEPARTEMENTS
            ////////////////////////////////////////////////////////
            Map<String, Random> aleaParDepartement = new HashMap<>();
            for (String dpt : listeDpts) {
            	aleaParDepartement.put(dpt, new Random(Configuration.get().seed + dpt.hashCode()));
            }
            
            MultiTask.forEach(listeDpts, (String dpt) -> {
                try {
                	Random random = aleaParDepartement.get(dpt);
	                if (fen != null) fen.afficher("Démarrage -  " + Configuration.get().getNomDepartement(dpt));
	
	                String ligne;
	                String[] donneesLigne;
	                File loopDir;
	                
	                
	                //initialisation du dossier de sortie
	                //date = new Date();
	                String dptResultats = dpt + "_" + parametres.terminaison; // utilisé dans les noms de fichiers aussi
	                String cheminResultats = racineResultats+ "/Resultats_"+dpt;
	                if (parametres.ficUnitesOeuvre ||
	                        parametres.ficCouts ||
	                        parametres.ficLongueurs ||
	                        parametres.ficLineaires ||
	                        parametres.ficNRO ||
	                        parametres.ficPM ||
	                        parametres.ficPBO ||
	                        fichiersTest) {
	                    loopDir = new File(cheminResultats);
	                    loopDir.mkdirs();
	                }
	                
	                if (fen != null) fen.afficher("Lecture du fichier immeubles");
	                ///////////////////////////////////////////////////////////////////////////
	                /// Lecture des immeubles du département (pour les coûts de colonne montante)
	                ///////////////////////////////////////////////////////////////////////////
	                
	                HashMap<String, int[]> listeImmeubles = new HashMap<>();
	                BufferedReader ficImmeubles = new BufferedReader(new FileReader(fichierImmeubles));
	                ficImmeubles.readLine(); // on passe la ligne d'en-tête
	                int nb_carac_to_select = dpt.length();
	                while ((ligne = ficImmeubles.readLine()) != null) {
	                    donneesLigne = ligne.split(";");
	                    if (donneesLigne[0].substring(0, nb_carac_to_select).equals(dpt)) {
	                        int[] nbImmeubles = new int[100];
	
	                        for (int i = 1; i < 99; i++) {
	                            if (!donneesLigne[i].equals("#DIV/0!")) {
	                                nbImmeubles[i] = Integer.parseInt(donneesLigne[i]);
	                            } else {
	                                nbImmeubles[i] = 0;
	                            }
	                        }
	
	                        listeImmeubles.put(donneesLigne[0], nbImmeubles);
	                    }
	                } 
	                ficImmeubles.close();
	
	                if (fen != null) fen.afficher("Initialisation des fichiers de sortie");
	                
	                //////////////////////////////////////////////////////////
	                /// INITIALISATION  DES FICHIERS DE SORTIE PAR DEPARTEMENT
	                //////////////////////////////////////////////////////////
	                                
	                
	                /// A. Shapefiles
	                
	                CoordinateReferenceSystem crs = Configuration.get().getCRS(dpt);
	                GeometryFactory gf = JTSFactoryFinder.getGeometryFactory(new Hints(Hints.JTS_SRID, Integer.parseInt(crs.getIdentifiers().iterator().next().getCode())));
	                
	                SimpleFeatureType typeShpNRO = BLO.getNROFeatureType(crs);
	                SimpleFeatureBuilder featureBuilderNRO = new SimpleFeatureBuilder(typeShpNRO);
	                List<SimpleFeature> featuresNRO = new ArrayList<>();
	                
	                SimpleFeatureType typeShpPM = ZAPM.getPMFeatureType(crs);
	                SimpleFeatureBuilder featureBuilderPM = new SimpleFeatureBuilder(typeShpPM);
	                List<SimpleFeature> featuresPM = new ArrayList<>();
	                
	                SimpleFeatureType typeShpAretes = AreteBLOM.getAreteFeatureType(crs);
	                SimpleFeatureBuilder featureBuilderAretes = new SimpleFeatureBuilder(typeShpAretes);
	                List<SimpleFeature> featuresAretes = new ArrayList<>();
	
	                /// B. Fichiers csv
	                
	                PrintWriter UOparPM = null; // fichier des UO par PM (GC, câbles, boîtiers)
	                if (parametres.ficUnitesOeuvre) {
	                    UOparPM = new PrintWriter(new FileWriter(cheminResultats + "/UnitesOeuvreParPM_"+ dptResultats +".csv"));
	                    UOparPM.println("Zone;NRO;PM;X;Y;Distance NRO-PM;"+UO.header(parametres, true, false, 0));
	                }
	                
	                PrintWriter csvDistributionL = null; // fichier de la distribution des longueurs de lignes PM-PBO par PM
	                if (parametres.ficLongueurs) {
	                    csvDistributionL = new PrintWriter(new FileWriter(cheminResultats + "/LongueursParPM_"+ dptResultats +".csv"));
	
	                    String header = "Zone;NRO;PM;Distance NRO-PM;Longueur min;Longueur max";
	                    for (int i = 0; i < 2000; i++) {
	                        header += ";" + i;
	                    }
	                    csvDistributionL.println(header);
	                }
	
	                PrintWriter csvCouts = null; // fichier des coûts par PM
	                if (parametres.ficCouts) {
	                    csvCouts = new PrintWriter(new FileWriter(cheminResultats + "/CoutsParNRO_"+ dptResultats +".csv"));
	                    csvCouts.println("Zone;NRO;"+Couts.header()); 
	                }
	
	                // initialisation des fichiers utilisés seulement lorsque fichiersTest = true
	                
	                HashMap<Integer, HashMap<Integer, Double>> statsRoutes = null;            
	                PrintWriter csvListePC = null;
	                PrintWriter csvAnalyseRoute = null;
	                PrintWriter csvAnalysePCIsoles5pc = null;
	                PrintWriter csvAnalysePCIsoles20pc = null;
	                PrintWriter csvAnalysePCIsoles30pc = null;
	                
	                if (fichiersTest) {
	                    
	                    statsRoutes = new HashMap<>();
	                    for (int i = 0; i < 9; i++) {
	                        statsRoutes.put(i, new HashMap<Integer, Double>());
	                    }
	                    
	                    csvListePC = new PrintWriter(new FileWriter(cheminResultats + "/PCParPM_"+ dptResultats +".csv"));
	                    csvListePC.println("NRO;PM;PC");
	
	                    csvAnalyseRoute = new PrintWriter(new FileWriter(cheminResultats + "/AnalyseRoute_"+ dptResultats +".csv"));
	                    String sCSV = "Dpt;ModePose;";
	                    for (int i = 1; i <= 200; i++) {
	                        sCSV += ";" + i;
	                    }
	                    csvAnalyseRoute.println(sCSV);
	
	                    csvAnalysePCIsoles5pc = new PrintWriter(new FileWriter(cheminResultats + "/PCIsoles5pc_"+ dptResultats +".csv"));
	                    csvAnalysePCIsoles5pc.println("Dpt;NRO;PC");
	
	                    csvAnalysePCIsoles20pc = new PrintWriter(new FileWriter(cheminResultats + "/PCIsoles20pc_"+ dptResultats +".csv"));
	                    csvAnalysePCIsoles20pc.println("Dpt;NRO;PC");
	
	                    csvAnalysePCIsoles30pc = new PrintWriter(new FileWriter(cheminResultats + "/PCIsoles30pc_"+ dptResultats +".csv"));
	                    csvAnalysePCIsoles30pc.println("Dpt;NRO;PC");
	                }       
	
	                int[] analyseGC = new int[5];
	                
	                int nbNRO = 0;
	                File d = new File(cheminReseau + dossierBLO+"/"+dossierBLO.replace("-old","")+"_"+dpt);
	                for (File f : d.listFiles()){
	                    if (parametres.zones.contains(f.getName().replace(dossierBLO.replace("-old","")+"_"+dpt+"_", "")))
	                        nbNRO+=(f.listFiles().length/3);
	                }
	                
	                if (fen != null) fen.initBarreDpt(nbNRO);
	                
	                Map<String, Integer> demandeDpt = parametres.getDemande(dpt);
	                
	                ////////////////////////////////////////////////////////
	                /// BOUCLE SUR LES ZONES MACRO CHOISIES PAR L'UTILISATEUR
	                ////////////////////////////////////////////////////////
	
	                for (String zone : parametres.zones){
	                
	                    File ficNRO = new File(cheminReseau + dossierNRO+"/"+dossierNRO+"_"+dpt+"/"+dossierNRO+"_"+dpt+"_"+zone+"/"+dossierNRO+"_"+dpt+"_"+zone+".csv");
	                    
	                    if (!ficNRO.exists()){
	                        System.out.println("Le département "+dpt+" et la zone "+zone+" sont d'intersection vide");
	                    } else {
	                        
	                        // lecture du regroupement des NRA en NRO
	                        HashMap<String, ArrayList<String>> NRONRA = new HashMap<>();
	                        BufferedReader fichierNRO = new BufferedReader(new FileReader(ficNRO));
	                        fichierNRO.readLine();
	                        while ((ligne = fichierNRO.readLine()) != null) {
	                            donneesLigne = ligne.split(";");
	                            String nro, nra;
	                            nra = donneesLigne[1];
	                            nro = donneesLigne[0];
	                            if (!NRONRA.containsKey(nro)) NRONRA.put(nro, new ArrayList<String>());
	                            NRONRA.get(nro).add(nra);
	                        }
	                        Set<String> listeNRO = NRONRA.keySet();
	                        UO uo = new UO(zone, true, true, parametres);
	                        fichierNRO.close();
	                        
	                        Couts couts = new Couts(); 
	                        String dossier = cheminReseau+dossierBLO+"/"+dossierBLO.replace("-old","")+"_"+dpt+"/"+dossierBLO.replace("-old","")+"_"+dpt+"_"+zone;
	                 
	                        ////////////////////////////////////////////////////////
	                        /// AJUSTEMENT DE LA DEMANDE CONSTRUITE A LA DEMANDE CIBLE
	                        ////////////////////////////////////////////////////////
	
	                        
	                        Map<String,Map<Integer,Noeud>> noeudsNRO = new HashMap<>();
	                        List<Noeud> noeudsDemande = new ArrayList<>();
	                        int demandeConstruite = 0;
	                        
	                        // on remplit noeudsNRO, et noeudsDemande par effet de bord
	                        int seuilPMint = parametres.seuilPM("int");
	                        for (String codeNRO : listeNRO){
	                            Map<Integer,Noeud> listeNoeudsNRO = Reseau.readNoeudsSelectDemande(dossier,codeNRO, noeudsDemande, seuilPMint);
	                            noeudsNRO.put(codeNRO, listeNoeudsNRO);
	                            for (Noeud n : listeNoeudsNRO.values()){
	                                demandeConstruite+=n.demandeLocaleTotale();
	                            }
	                        }
	                        System.out.println("Demande totale construite dans le département pour la zone "+zone+" :"+demandeConstruite);
	
	                        this.adapteDemande(noeudsDemande, demandeConstruite, demandeDpt.get(zone), zone, seuilPMint, random);
	                        int newDemande = 0;
	                        for (Noeud n : noeudsDemande){
	                            newDemande += n.demandeLocaleTotale();
	                        }
	                        System.out.println("Nouvelle demande en "+zone+" : "+newDemande);
	
	                        ////////////////////////////////////////////////////////
	                        /// BOUCLE SUR LES NRO 
	                        ////////////////////////////////////////////////////////         
	                
	                        for (String codeNRO : listeNRO) {
	                            if (fen != null) fen.debutNRA("NRO " + codeNRO.replace("\"", ""));
	                            
	                            BLO blo = new BLO(codeNRO, Reseau.readCentre(dossier, codeNRO), noeudsNRO.get(codeNRO), Reseau.readAretes(dossier,codeNRO), zone, parametres);
	                            blo.simplification(); // on fusionne les arêtes qui peuvent l'être
	                            blo.posePM();
	                            blo.calculerDemandeAretes(); // arête par arête, la demande étant utile pour le calcul du mode pose
	                            blo.setModesPose(coloriage, dossier);
	                            blo.calculerAutresUOAretes(); // arête par arête
	                            blo.calculResultatsParPM(); // là on agrège en parcourant l'arbre encore une fois
	                            
	                            if (parametres.ficLineaires) featuresAretes.addAll(blo.getAretes(gf, featureBuilderAretes));
	                            if (parametres.ficPBO) blo.printShapePBO(cheminResultats, crs, gf);
	                            
	                            int[] immeubles = immeubles(NRONRA.get(codeNRO), listeImmeubles);
	                            UO uoNRO = blo.computeAndGetUo(immeubles);
	                            csvUONatparNRO.println(dpt+";"+zone+";"+codeNRO+";"+uoNRO.print(parametres, 1));
	                            uo.addUO(uoNRO);
	                            
	                            if (parametres.ficNRO) featuresNRO.add(blo.getFeatureNRO(gf ,featureBuilderNRO));
	
	                            blo.clearArbre(); // attention ici on suppose qu'on n'a plus besoin de l'arbre contenu dans blo et on fait blo.root = null
	                            
	                            ////////////////////////////////////////////////////////
	                            /// IMPRESSION DANS LES FICHIERS DE SORTIE
	                            ////////////////////////////////////////////////////////
	                            
	                            if (parametres.ficUnitesOeuvre){
	                                for (String s : blo.stringsUO(0)){
	                                    UOparPM.println(s);
	                                }
	                            }
	                            
	                            Couts c = blo.computeAndGetCouts(coutsUnitaires);
	                            if (this.coutsListener != null) {
	                            	this.coutsListener.accept(c);
	                            }
	                            couts.add(c);
	                            if(parametres.ficCouts){
	                                csvCouts.println(zone+";"+codeNRO+";"+c.print());
	                            }
	                            
	                            if (parametres.ficLongueurs){
	                                for (String s : blo.stringsDistriLongueurs()){
	                                    csvDistributionL.println(s);
	                                }
	                            }
	                            
	                            if (parametres.ficPM){
	                                featuresPM.addAll(blo.getFeaturesPM(gf, featureBuilderPM));
	                            }
	
	                            for(String s : blo.getNROPM(dpt)){
	                                csvAnalyseNROPM.println(s);
	                            }
	
	                            int[] gc = blo.getPBO();
	                            for (int i = 0;i<analyseGC.length;i++){
	                                analyseGC[i] += gc[i];
	                            }
	                            
	                            System.out.println("Fin du NRO n°" + codeNRO);
	                            if (fen != null) fen.finNRA();
	                        }
	                
	                        // "flush" des fichiers de résultats départementaux
	                        if (parametres.ficUnitesOeuvre)
	                            UOparPM.flush();
	
	                        if (parametres.ficLongueurs) 
	                            csvDistributionL.flush();               
	
	                        if (parametres.ficCouts) 
	                            csvCouts.flush();                
	
	                        if (fichiersTest) {
	                            csvListePC.flush();
	                            csvAnalysePCIsoles5pc.flush();
	                            csvAnalysePCIsoles20pc.flush();
	                            csvAnalysePCIsoles30pc.flush();
	                            csvAnalyseRoute.flush();
	                        }
	
	                        uoZones.get(zone).addUO(uo);
	                        
	                        csvUONatDetail.println(dpt+";"+zone+";"+uo.print(parametres, 2));
	                        csvCoutsNat.println(dpt+";"+zone+";"+couts.print());
	                        csvAnalyseGC.print(dpt+";"+zone);
	                        for (int i : analyseGC){
	                            csvAnalyseGC.print(";"+i);
	                        }
	                        csvAnalyseGC.println();
	                    }
	                }   
	                
	                // Fermeture des fichiers de résultats
	                if (parametres.ficUnitesOeuvre)
	                    UOparPM.close();
	                
	                if (parametres.ficLongueurs) 
	                    csvDistributionL.close();                
	                
	                if (parametres.ficCouts) 
	                    csvCouts.close();                
	                
	                if (fichiersTest) {
	                    csvListePC.close();
	                    csvAnalysePCIsoles5pc.close();
	                    csvAnalysePCIsoles20pc.close();
	                    csvAnalysePCIsoles30pc.close();
	                    csvAnalyseRoute.close();
	                }
	                
	                //Creation des ShapeFiles
	                if (parametres.ficNRO)
	                    Shapefiles.printShapefile(cheminResultats+"/NRO_"+dpt, typeShpNRO, featuresNRO);
	                
	                if (parametres.ficPM)
	                    Shapefiles.printShapefile(cheminResultats + "/PM_" + dpt, typeShpPM, featuresPM);
	                
	                if (parametres.ficLineaires)
	                    Shapefiles.printShapefile(cheminResultats + "/Aretes_" + dpt, typeShpAretes, featuresAretes);
	
	                if (fen != null) fen.finTraitementDpt();
	                nbDptTraites++;     
	                System.out.println("Fin pour le département "+dpt);
                } catch (Exception e) {
                    //throw;
                    if (fen != null) JOptionPane.showMessageDialog(fen, e.toString());
                    e.printStackTrace();
                }
            });
            
            for (String zone : parametres.zones){
                csvUONatCouts.println(zone+";"+uoZones.get(zone).print(parametres, 1));
            }
            
            csvUONatCouts.close();
            csvUONatDetail.close();
            csvUONatparNRO.close();
            csvCoutsNat.close();
            csvAnalyseGC.close();
            csvAnalyseNROPM.close();
            
            if (Configuration.get().dossierCommandesPostDeploiement != null) {
            	File f = new File(Configuration.get().dossierCommandesPostDeploiement);
            	if (f.exists()) {
            		for (var command : f.listFiles()) {
            			if (!command.getName().endsWith(".sh") && !command.getName().endsWith(".cmd")) continue;
            			
            			ProcessBuilder pb = new ProcessBuilder(
            					command.getName().endsWith(".sh") ? "sh" : "cmd.exe",
            					command.getName().endsWith(".sh") ? "-c" : "/C",
            					"\"" + command.getAbsolutePath() + "\" \"" + dir + "/UONatDetail_" + parametres.terminaison + ".csv\""
            			);

            	        Process p = pb.start();
            	        var result = p.waitFor();
            	        System.out.println(String.format("Command '%s' terminer avec le statut : %d", command.getName(), result));
            	        try (var streamReader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            	        	streamReader.lines().forEach(System.err::println);
            	        }
            		}
            	}
            }
            
            if (fen != null) fen.finTraitement(System.currentTimeMillis() - start);

        } catch (Exception e) {
            //throw;
        	if (fen != null) JOptionPane.showMessageDialog(fen, e.toString());
            e.printStackTrace();
        }
    }

    public int getAvancement() {
        if (listeDpts.isEmpty())
            return 0;
        else return Math.round(100*nbDptTraites/listeDpts.size());
    }
    
    private void adapteDemande(List<Noeud> noeudsDemande, int demandeConstruite, int demandeVoulue, String zone, int seuilPMint, Random PRNG){
        System.out.println("Appel de adapteDemande");
        System.out.println("Zone : "+zone);
        System.out.println("Demande existante à partir du fichier des LP : "+demandeConstruite);
        System.out.println("Demande cible : "+demandeVoulue);
        int demande = demandeConstruite;
        int nbNoeuds = noeudsDemande.size();
        while (demande > demandeVoulue & nbNoeuds > 0){
            int i = PRNG.nextInt(nbNoeuds); // renvoie un nombre aléatoire de façon équiprobable entre 0 et nbNoeuds-1
            Noeud n = noeudsDemande.get(i);
            if (n.decreaseDemande(zone, PRNG)){
                noeudsDemande.remove(n);
                nbNoeuds--;
            }
            demande--;
        }
        if (nbNoeuds > 0){
            while (demande < demandeVoulue){
                int i = PRNG.nextInt(nbNoeuds); // renvoie un nombre aléatoire de façon équiprobable entre 0 et nbNoeuds-1
                Noeud n = noeudsDemande.get(i);
                n.increaseDemande(zone, seuilPMint, PRNG);
                demande++;
            }
        }
    }
    
    private int[] immeubles(List<String> NRAduNRO, Map<String, int[]> listeImmeubles){
        int[] res = new int[100];
        for (String codeNRA : NRAduNRO){
            int[] immeubles=listeImmeubles.get(codeNRA);
            if (immeubles != null) {
                for (int i = 0; i < immeubles.length; i++) { 
                    res[i]+= immeubles[i];
                }
            }
            else {
                System.out.println("Problème base immeubles pour le NRA n°"+codeNRA);
            }
        }
        return res;
    }
    
    public Consumer<Couts> getCoutsListener() {
		return coutsListener;
	}

	public void setCoutsListener(Consumer<Couts> coutsListener) {
		this.coutsListener = coutsListener;
	}
}