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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class BLO {
    
    private final String codeNRO;
    private AreteBLOM root; // arête virtuelle racine de l'arbre représentant le réseau
    private final String zoneMacro; // ZTD ou ZMD_AMII ou ZMD_RIP
    private final Set<String> zones; // {ZTD_HD, ZTD_BD} ou {ZMD}
    private int nbLignes;
    private final Parametres parametres;
    
    private List<Noeud> listePMint;
    private Map<String,List<ZAPM>> ResParPM; // par zone, une liste de classe où sont stockés UO et autres infos, par PM
    private Map<String,UO> transports;
    private UO uo;
    private Couts c;

    public BLO(String codeNRO, int centre, Map<Integer,Noeud> noeuds, Map<Long,Arete> aretes,  String zone, Parametres parametres) {
        System.out.println("Zone : "+zone);
        System.out.println("Nombre de noeuds : "+ noeuds.size());
        System.out.println("Nombre d'arêtes : "+ aretes.size());
        this.codeNRO = codeNRO;
        
        // passage des zones de régulation (ZTD/AMII/RIP) au zones "architecture réseau" (ZTD_HD, ZTD_BD, ZMD)
        zoneMacro = zone;
        zones = new HashSet<>(); 
        if (zone.equals("ZTD")){
            zones.add("ZTD_HD");
            zones.add("ZTD_BD");
        }
        else zones.add("ZMD");
        
        this.parametres = parametres;

        root = new AreteBLOM(noeuds.get(centre));
        this.buildTree(noeuds,aretes);
        System.out.println("Test root 2 - Le NRO "+codeNRO+" correspond au noeud réseau : " + root.n.id);
        System.out.println("Test root 2 - Ses coordonnées sont : X : "+root.n.coord[0]+" - Y : "+root.n.coord[1]);
    }
    
    private void buildTree(Map<Integer,Noeud> noeuds, Map<Long,Arete> aretes){
        System.out.println("Construction de l'arbre en AreteBLOM");
        for (Noeud noeud : noeuds.values()){
            if (noeud.demandeLocaleTotale() > 0){
                List<Long> path = noeud.getPath();
                if (path == null) {
                    System.out.println("Path null pour le noeud " + noeud.id);
                } else {
                    
                    // Parcours du chemin entre le NRO et le noeud (PBO)
                    // pour ajouter les noeuds à l'arbre BLOM
                    // mere est initialisée à "root" (origine virtuelle du réseau)
                    // puis la fille devient mere à mesure du parcours
                    AreteBLOM mere = root;
                    for (Long id : path){
                        boolean ajout = true;
                        // Parcours des AreteBLOM filles de l'AreteBLOM mere
                        // si l'une d'entre elle correspond à l'ID du path, elle
                        // est ajoutée à l'arbre
                        for (AreteBLOM fille : mere.getFilles()){
                            if (fille.id == id){
                                ajout = false;
                                fille.addNoeudLocal(noeud, zones);
                                mere = fille;
                                break;
                            }
                        }
                        if (ajout){
                            Arete a = aretes.get(id);
                            Noeud n = noeuds.get(a.getAutreExtremite(mere.n.id));
                            try{
                                AreteBLOM fille = new AreteBLOM(a, n, mere.n);
                                fille.addNoeudLocal(n, zones);
                                mere.addFille(fille);
                                mere = fille;
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        root.calculDemandeRoot();
    }
    
    public void simplification(){
        //Construction de l'arbre orienté simplifié
        AreteBLOM newRoot = new AreteBLOM(root, 0, new ArrayList<>());
        System.out.println("Demande locale au NRO : "+newRoot.n.demandeLocaleTotale());
        for (AreteBLOM ar : root.getFilles()){
            ar.fusionAretes(newRoot,0, new ArrayList<>());
        }
        this.root = newRoot;
        nbLignes = this.root.totalLignes();
        System.out.println("Nb de lignes au NRO : "+nbLignes);
    }

    public void posePM(){
        
        // placement des PM extérieurs
        for (String zone : zones){
            int nbPM;
            if (parametres.utiliseMediane) nbPM = root.posePMExtMediane(zone, parametres.maxDistPMPBO, parametres.seuilPM(zone));
            else nbPM = root.posePMExtMoyenne(zone, parametres.maxDistPMPBO, parametres.seuilPM(zone));
            System.out.println("Nombre de PM ext en zone "+zone+" : "+nbPM);
        }
        
        // listing des PM intérieurs (et calcul de la distance au NRO pour tous les noeuds)
        listePMint = new  ArrayList<>();
        if (zoneMacro.equals("ZTD")){
            root.listingPMint(listePMint, 0);
            System.out.println("Nombre de PM int : "+listePMint.size());
        }
        // numérotation globale des PM dépendant du NRO
        int prochainNumero = 1;
        for(String zone: zones){
            prochainNumero = root.numerotePM(zone, prochainNumero);
        }
        for (Noeud n : this.listePMint){
            n.numPM = prochainNumero;
            prochainNumero++;
        }
    }
    
    public void setModesPose(boolean coloriage, String dossier){
        
        if (coloriage){
            double[] pourcentages = this.getPourcentagesGC(codeNRO, dossier, "ModesPose");
            double pourcentageAerien = pourcentages[0]+pourcentages[1]+pourcentages[2];
            double pourcentagePT = pourcentages[4];

            int niveauRoot = root.calculNiveau();
            double longueurTotale = root.longueurTotale();

            double seuilAerien = longueurTotale*pourcentageAerien;
            double longueurPT = longueurTotale*pourcentagePT;
            double seuilPleineTerreAerien = seuilAerien + longueurPT*parametres.partReconstrPTAerien;
            double seuilPleineTerreSouterrain = seuilAerien + longueurPT;

            int niveau = 1;
            double longueurParcourue = 0.0;
            while (niveau < niveauRoot){
                longueurParcourue = root.setModesPose(niveau, seuilAerien, seuilPleineTerreAerien, seuilPleineTerreSouterrain, longueurParcourue);
                niveau++;
            }

            System.out.println("Longueur totale : "+longueurTotale);
            System.out.println("LongueurP finale : "+longueurParcourue);
            System.out.println("Niveau de la racine : "+niveauRoot);
        } else {
            
            String modeReconstrGCTransport = parametres.modeReconstrGCTransport;
            String modeReconstrCDistrib = parametres.modeReconstrGCDistrib;
            
            String enAerienPattern = "En a.*rien";
            
            if("Tout reconstruire en souterrain".equals(modeReconstrGCTransport)) {
                root.setModesPose(false, true, Pattern.matches(enAerienPattern,  modeReconstrCDistrib));
                System.out.println("Forçage en souterrain de l'aérien");
            } else {
                root.setModesPose(Pattern.matches(enAerienPattern,  modeReconstrGCTransport), false, Pattern.matches(enAerienPattern,  modeReconstrCDistrib));
            }
            //root.setModesPose();
        }
    }
    
    public void calculerUOAretes(){
        for (AreteBLOM a : root.getFilles()){
            a.calculUO(zones, parametres); // fonction récursive
        }
    }
    
    public void calculerDemandeAretes() {
        for (AreteBLOM a : root.getFilles()){
            a.calculDemande(zones, parametres); // fonction récursive
        }
    }
    
    public void calculerAutresUOAretes() {
        for (AreteBLOM a : root.getFilles()){
            a.calculAutresUO(zones, parametres); // fonction récursive
        }
    }
    
    public List<SimpleFeature> getAretes(GeometryFactory gf, SimpleFeatureBuilder featureBuilderLineaires){
        List<SimpleFeature> aretes = new ArrayList<>();
        for (AreteBLOM a : root.getFilles()){
            aretes.addAll(a.getFeatures(codeNRO+"_"+zoneMacro.replace("ZMD_", ""), zoneMacro.replace("ZMD_", ""), zones, parametres.calibre, gf, featureBuilderLineaires));
        }
        return aretes;
    }

    public void calculResultatsParPM(){
        this.ResParPM = new HashMap<>();
        this.transports = new HashMap<>();
        for (String zone : zones){
            // distribution
            List<ZAPM> res = root.agregeResultats(zone, 0, parametres);
            this.ResParPM.put(zone, res);
            System.out.println("Nombre de ZAPM en zone "+zone+" : "+(res.size()));
            
            // transport
            UO transport = new UO(zone, false, true, parametres);
            for (AreteBLOM a : root.getFilles()){
                List<Lineaires> linTransport = a.getLineairesTransport(parametres, zone);
                for (Lineaires lin : linTransport){
                    transport.addLineaires(lin);
                }
                transport.addIncomingCables(a.getCables(false, zone), false, parametres.calibre, parametres.getCalibreMin());
            }
            transports.put(zone, transport);   
        }
    }
    
    public UO computeAndGetUo(int[] immeubles){
        this.uo = new UO(zoneMacro, true, true, parametres);
        for (String zone : zones){
            for (ZAPM zapm : ResParPM.get(zone)){
                this.uo.addUO(zapm.getUO());
            }
            this.uo.addUO(transports.get(zone));
        }
        if (zoneMacro.equals("ZTD"))
            this.uo.setPMint(listePMint, parametres);
        this.uo.setVertical(immeubles, parametres);
        this.uo.setNRO(parametres);
        return this.uo;
    }
    
    public List<String> stringsUO(int detail){
        List<String> liste = new ArrayList<>();
        for (String zone : zones){
            int i = 1;
            for (ZAPM zapm : this.ResParPM.get(zone)){
                liste.add(zoneMacro+";"+codeNRO+";"+zapm.getStringUO(codeNRO+"_"+zone+"_"+i, parametres, detail));
                i++;
            }
            liste.add(zoneMacro+";"+codeNRO+";Transport_"+zone+";"+";"+";"+";"+transports.get(zone).print(parametres, detail));
        }
        return liste;
    }
    
    public List<String> stringsDistriLongueurs(){
        List<String> liste = new ArrayList<>();
        for (String zone : zones){
            int i = 1;
            for (ZAPM zapm : this.ResParPM.get(zone)){
                liste.add(zoneMacro+";"+codeNRO+";"+codeNRO+"_"+zone+"_"+i+";"+zapm.getStringDistri());
                i++;
            }
        }
        return liste;
    }
    
    public List<SimpleFeature> getFeaturesPM(GeometryFactory gf, SimpleFeatureBuilder featureBuilderPM){
        List<SimpleFeature> res = new ArrayList<>();
        for(String zone : zones){
            for (ZAPM zapm : ResParPM.get(zone)){
                res.add(zapm.getFeaturePM(codeNRO+"_"+zoneMacro.replace("ZMD_", ""), zoneMacro.replace("ZMD_", ""), gf, featureBuilderPM));
            }
            if (zone.equals("ZTD_HD")){
                for (Noeud pm : listePMint){
                    featureBuilderPM.add(gf.createPoint(new Coordinate(pm.coord[0], pm.coord[1])));
                    featureBuilderPM.add(zoneMacro.replace("ZMD_", ""));
                    featureBuilderPM.add(codeNRO+"_"+zoneMacro.replace("ZMD_", ""));
                    featureBuilderPM.add("PM_int");
                    featureBuilderPM.add(pm.numPM);
                    featureBuilderPM.add(pm.demandeLocaleInt());
                    featureBuilderPM.add("");
                    res.add(featureBuilderPM.buildFeature(null));
                }
            }
        }
        return res;
    }
    
    public static SimpleFeatureType getNROFeatureType(CoordinateReferenceSystem crs){
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("NRO");
        builder.setCRS(crs);
        builder.add("the_geom", Point.class);
        builder.length(4).add("ZONE_MACRO", String.class);
        builder.length(13).add("ID_NRO", String.class);
        builder.add("NB_LIGNES", Integer.class);
        builder.add("NB_FIBRES", Integer.class);
        return builder.buildFeatureType();
    }
    
    SimpleFeature getFeatureNRO(GeometryFactory gf, SimpleFeatureBuilder featureBuilder){
        featureBuilder.add(gf.createPoint(new Coordinate(root.n.coord[0], root.n.coord[1])));
        featureBuilder.add(zoneMacro.replace("ZMD_", ""));
        featureBuilder.add(codeNRO+"_"+zoneMacro.replace("ZMD_", ""));
        featureBuilder.add(this.nbLignes);
        featureBuilder.add(this.uo.getNbIncomingFibres(false));
        return featureBuilder.buildFeature(null);
    }
    
    public Couts computeAndGetCouts(CoutsUnitaires couts){
        c = uo.calculCouts(couts, parametres);
        return c;
    }
    
    public int[] getPBO(){
        return this.uo.getPBO();
    }
    
    public void printShapePBO(String cheminResultats, CoordinateReferenceSystem crs, GeometryFactory gf){
        SimpleFeatureType typeShpPBO = AreteBLOM.getPBOFeatureType(crs, zoneMacro);
        SimpleFeatureBuilder featureBuilderPBO = new SimpleFeatureBuilder(typeShpPBO);
        List<SimpleFeature> featuresPBO = root.getFeaturesPBO(zoneMacro.replace("ZMD_", ""), codeNRO+"_"+zoneMacro.replace("ZMD_", ""), zones, gf, featureBuilderPBO);
        Shapefiles.printShapefile(cheminResultats + "/PBO_" + codeNRO+"_"+zoneMacro.replace("ZMD_", ""), typeShpPBO, featuresPBO);
    }

    public void clearArbre(){
        root = null; // on libère de la place en mémoire car on n'a plus besoin de l'arbre
    }

    public List<String> getNROPM(String dpt){
        List<String> res = new ArrayList<>();
        String debut = dpt+";"+zoneMacro+";"+codeNRO;
        for(String zone : zones){
            for (ZAPM zapm : ResParPM.get(zone)){
                res.add(debut+";"+(res.size()+1)+";"+zapm.getNROPM(parametres)+";"+String.valueOf(zapm.percentile(0.10)).replace(".", ",")+";"+String.valueOf(zapm.percentile(0.50)).replace(".", ",")+";"+String.valueOf(zapm.percentile(0.90)).replace(".", ","));
            }
            if (zone.equals("ZTD_HD")){
                for (Noeud pm : listePMint){
                    res.add(debut+";"+(res.size()+1)+";PMint;"+String.valueOf(pm.distanceAuCentre).replace(".", ",")+";"+pm.demandeLocaleInt()); 
                }
            }
            res.add(debut+";0;NRO;;"+transports.get(zone).printIncomingCables(false));
        }
        return res;
    }
    
    private double[] getPourcentagesGC(String codeNRO, String dossierNRO, String pourcentages){
        int n = 11;
        double[] res = new double[n];
        String line;
        String[] fields;
        File modesPose = new File(dossierNRO+"/"+pourcentages+"_"+codeNRO+".csv");
        try{
            BufferedReader reader = new BufferedReader(new FileReader(modesPose));
            reader.readLine();
            line = reader.readLine();
            fields = line.split(";");
            if (fields[0].equals(codeNRO)){
                for (int i = 0;i<n;i++){
                    res[i] = Double.parseDouble(fields[i+1].replace(",","."));
                }
            }
            reader.close();
        }catch(IOException e){}
        return res;
    }
    
}
