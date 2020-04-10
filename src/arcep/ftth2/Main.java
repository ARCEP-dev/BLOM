/*
 * Copyright (c) 2020, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
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

import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import java.util.Random;

public class Main {
    
    // Déclaration des chemins statiques (utilisés dans plusieurs méthodes ici)
    public static String cheminReseau;
    
    // localisation et radical des noms de shapefiles de GC
    static String adresseShapesGC;
    static String nameShapeGC;
    
    static int seed = 0;
    
    // localisation et radical des noms de shapefiles du réseau routier
    static String adresseShapesRoutes;
    static String nameShapeRoutes;
    
    private static CoordinateReferenceSystem crsLambertIIEtendu;
    private static CoordinateReferenceSystem crsLambert93;
    private static CoordinateReferenceSystem crsAntilles;
    private static CoordinateReferenceSystem crsGuyane;
    private static CoordinateReferenceSystem crsReunion;
    private static Map<String, String> nomDepartements;
    
    public static Random PRNG = new Random();
    
    private static boolean crsSortieLambertIIEtendu;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        initCRS();
        initNomsDepartements();

        
        // Lecture des chemins des fichiers d'entrée
        IniReader ini = new IniReader("BLOM.ini");
        
        // Initialisation du chemin de la racine des fichiers intermédiaires
        cheminReseau = ini.getVal("cheminReseau");
        
        // Initialisation du chemin des fichiers d'entrée de grande taille
        adresseShapesGC = ini.getVal("adresseShapesGC");
        nameShapeGC = ini.getVal("nameShapeGC");
        adresseShapesRoutes = ini.getVal("adresseShapesRoutes");
        nameShapeRoutes = ini.getVal("nameShapeRoutes");
        
        // Emplacement commun des fichiers d'entrée hors exceptions de grande taille à placer sur un disque local
        String inputFiles = ini.getVal("inputFiles");
        
        // fichiers décrivant le réseau cuivre
        // Chacun des 4 fichiers couvre le territoire national (tous les départements)
        String adresseLP = ini.getVal("adresseLP");
        String adresseSR = inputFiles+ini.getVal("fichierSR");
        String adresseNRA = inputFiles+ini.getVal("fichierNRA");
        String adresseCollecte = inputFiles+ini.getVal("fichierCollecte");
        String[] fichiersCuivre = new String[]{adresseLP, adresseSR, adresseNRA, adresseCollecte};
        
       // shapefiles des départements et liste des départements voisins
        String adresseDptsLimitrophes = inputFiles+ini.getVal("fichierDeptLimitrophes");
        String adresseShapesDptsMetro = inputFiles+ini.getVal("adresseShapesDpts")+"FranceMetro"; 
        String shape971 = inputFiles+ini.getVal("adresseShapesDpts")+"971";
        String shape972 = inputFiles+ini.getVal("adresseShapesDpts")+"972";
        String shape973 = inputFiles+ini.getVal("adresseShapesDpts")+"973";
        String shape974 = inputFiles+ini.getVal("adresseShapesDpts")+"974";
        String shape976 = inputFiles+ini.getVal("adresseShapesDpts")+"976";
        String[] shapesDOM = new String[]{shape971, shape972, shape973, shape974, shape976};
        String nameShapeDpts = ini.getVal("nameShapeDpts");
        // les attributs 2 et 3 doivent correspondre aux codex et aux noms du département
        
        // fichiers de forme décrivant les zones de régulation (ZTD_HD, ZTD_BD, ZMD_AMII, ZMD_RIP) afin d'affecter une zone à chaque PC/noeud du réseau
        String adresseShapeZTD = inputFiles+ini.getVal("fichierShapeZTD");
        // on s'attend à un shapefile avec un objet identifié "PHD" et un objet identifié "PBD" (attribut 1) qui définissent les deux sous-zones de la ZTD
        String adresseShapeAMII = inputFiles+ini.getVal("fichierShapeAMII");
        // les deux premiers caractères de l'attribut 1 doivent correspondent au département
        String dossierCommunes = inputFiles+ini.getVal("nomDossierCommunes"); // dossier contenant Communes_01.csv, Communes_02.csv, etc avec la zone de régulation ("ZTD, "AMII" ou "RIP") associée à commune (identifiée par son code INSEE)
        String[] fichiersZonage = new String[]{adresseShapeZTD, adresseShapeAMII, dossierCommunes};
        
        // les deux entrées du module de déploiement
        // les demandes des différents fichiers seront sommées au moment de l'initalisation
        String fichierDemandeCible1 = inputFiles+ini.getVal("fichierDemandeCible1");
        String fichierDemandeCible2 = inputFiles+ini.getVal("fichierDemandeCible2");
        //String ficherDemandeCible3 = ...
        //...
        String[] demandeCible = new String[]{fichierDemandeCible1, fichierDemandeCible2};
        
        // le fichier décrivant la distribution du nombre de locaux par immeuble, issu de la base propriétaire du CEREMA ici
        String fichierImmeubles = inputFiles+ ini.getVal("fichierImmeubles");
        
        // le fichier de coûts unitaires (il n'est pas utilisé dans cette version)
        String fichierCoutsUnitaires = inputFiles+ini.getVal("fichierCoutsUnitaires");
        
        // Chemin du répertoire où seront écrits les résultats
        String racineResultats = ini.getVal("racineResultats");
        
        // mise en place d'un seed pour le générateur de nombres pseudo-aléatoires
        seed = Integer.parseInt(ini.getVal("seed"));
        if (seed > 0) {
            PRNG = new Random(seed);
        }
        
        // option permettant de tracer les fichiers de forme en sortie dans le SCR Lambert II Etendu pour la
        // France métropolitaine (par défaut le SCR est Lambert 93)
        crsSortieLambertIIEtendu = false;
        if (ini.getVal("CRS_Sortie") != null) {
            if(!ini.getVal("CRS_Sortie").contains("93")) crsSortieLambertIIEtendu = true;
        }
        
        // Pondérations par défaut pour le tracé du réseau
        HashMap<String, Double> parametresReseau = new HashMap<>();
        parametresReseau.put("facteurConduite", 1.0);
        parametresReseau.put("facteurAerien", 1.0);
        parametresReseau.put("facteurPleineTerre", 1.0);
        
        if (ini.getVal("facteurConduite") != null) parametresReseau.replace("facteurConduite", Double.valueOf(ini.getVal("facteurConduite")));
        if (ini.getVal("facteurAerien") != null) parametresReseau.replace("facteurAerien", Double.valueOf(ini.getVal("facteurAerien")));
        if (ini.getVal("facteurPleineTerre") != null) parametresReseau.replace("facteurPleineTerre", Double.valueOf(ini.getVal("facteurPleineTerre")));
        
        parametresReseau.put("facteurVdO", Math.sqrt(2));
        if (ini.getVal("facteurVdO") != null) parametresReseau.replace("facteurVdO", Double.valueOf(ini.getVal("facteurVdO")));
        
        ////////////////////////////////////////////////
                
        FenPrincipale fenPrincipale = new FenPrincipale(adresseShapesGC, adresseShapesRoutes,
                fichiersCuivre,
                adresseDptsLimitrophes, adresseShapesDptsMetro, shapesDOM, nameShapeDpts,
                fichiersZonage,
                demandeCible, fichierImmeubles,
                fichierCoutsUnitaires, racineResultats, parametresReseau);
    }
    
    public static String getShapefileReseau(String type, String dpt){
        switch(type){
            case "GC":
                return adresseShapesGC + nameShapeGC.replace("[dpt]", dpt) + ".shp";
                //return adresseShapesGC+"/"+dpt+"/"+nameShapeGC+"_"+dpt+".shp";
            case "routier":
                return adresseShapesRoutes + nameShapeRoutes.replace("[dpt]", dpt) + ".shp";
                //return adresseShapesRoutes+"/"+dpt+"/"+nameShapeRoutes+"_"+dpt+".shp";
            default:
                return "ERROR!";
        }
    }
    
    private static void initCRS(){
        try{
            crsLambertIIEtendu = CRS.parseWKT("PROJCS[\"NTF (Paris) / Lambert zone II"
                    + "\",GEOGCS[\"NTF (Paris)\",DATUM[\"Nouvelle_Triangulation_Francaise_Paris\","
                    + "SPHEROID[\"Clarke 1880 (IGN)\",6378249.2,293.4660212936269,AUTHORITY[\"EPSG\",\"7011\"]],"
                    + "TOWGS84[-168,-60,320,0,0,0,0],AUTHORITY[\"EPSG\",\"6807\"]],"
                    + "PRIMEM[\"Paris\",2.33722917,AUTHORITY[\"EPSG\",\"8903\"]],"
                    + "UNIT[\"grad\",0.01570796326794897,AUTHORITY[\"EPSG\",\"9105\"]],"
                    + "AUTHORITY[\"EPSG\",\"4807\"]],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
                    + "PROJECTION[\"Lambert_Conformal_Conic_1SP\"],PARAMETER[\"latitude_of_origin\",52],"
                    + "PARAMETER[\"central_meridian\",0],PARAMETER[\"scale_factor\",0.99987742],"
                    + "PARAMETER[\"false_easting\",600000],PARAMETER[\"false_northing\",2200000],"
                    + "AUTHORITY[\"EPSG\",\"27572\"],AXIS[\"X\",EAST],AXIS[\"Y\",NORTH]]");
            crsLambert93 = CRS.parseWKT("PROJCS[\"RGF93 / Lambert-93"
                    + "\",GEOGCS[\"RGF93\",DATUM[\"Reseau_Geodesique_Francais_1993\","
                    + "SPHEROID[\"GRS 1980\",6378137,298.257222101,AUTHORITY[\"EPSG\",\"7019\"]],"
                    + "TOWGS84[0,0,0,0,0,0,0],AUTHORITY[\"EPSG\",\"6171\"]],"
                    + "PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,"
                    + "AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4171\"]],"
                    + "UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
                    + "PROJECTION[\"Lambert_Conformal_Conic_2SP\"],PARAMETER[\"standard_parallel_1\",49],"
                    + "PARAMETER[\"standard_parallel_2\",44],PARAMETER[\"latitude_of_origin\",46.5],"
                    + "PARAMETER[\"central_meridian\",3],PARAMETER[\"false_easting\",700000],"
                    + "PARAMETER[\"false_northing\",6600000],AUTHORITY[\"EPSG\",\"2154\"],AXIS[\"X\",EAST],AXIS[\"Y\",NORTH]]");
            crsAntilles = CRS.parseWKT("PROJCS[\"WGS 84 / UTM zone 20N"
                    + "\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\","
                    + "SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],"
                    + "PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],"
                    + "AUTHORITY[\"EPSG\",\"4326\"]],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
                    + "PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",-63],"
                    + "PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],AUTHORITY[\"EPSG\",\"32620\"],"
                    + "AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]]");
            crsGuyane = CRS.parseWKT("PROJCS[\"WGS 84 / UTM zone 22N\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\","
                    + "SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],"
                    + "PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],"
                    + "AUTHORITY[\"EPSG\",\"4326\"]],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
                    + "PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",-51],"
                    + "PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],AUTHORITY[\"EPSG\",\"32622\"],"
                    + "AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]]");
            crsReunion = CRS.parseWKT("PROJCS[\"WGS 84 / UTM zone 40S\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\","
                    + "SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],"
                    + "PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],"
                    + "AUTHORITY[\"EPSG\",\"4326\"]],UNIT[\"metre\",1,AUTHORITY[\"EPSG\",\"9001\"]],"
                    + "PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",57],"
                    + "PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",10000000],"
                    + "AUTHORITY[\"EPSG\",\"32740\"],AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]]");
        }catch(FactoryException e){
            System.out.println("Problème avec l'initialisation des CRS");
            e.printStackTrace();
        }
    }
    
    public static CoordinateReferenceSystem getCRS(String dpt){
        switch(dpt){
            case "971":
            case "972":
                return crsAntilles;
            case "973":
                return crsGuyane;
            case "974":
                return crsReunion;
            default:
                if (crsSortieLambertIIEtendu) return crsLambertIIEtendu;
                else return crsLambert93;
        }
    }
    
    private static void initNomsDepartements(){
        nomDepartements = new HashMap<>();
        nomDepartements.put("01", "Ain");
        nomDepartements.put("02", "Aisne");
        nomDepartements.put("03", "Allier");
        nomDepartements.put("04", "Alpes-de-Haute-Provence");
        nomDepartements.put("05", "Hautes-Alpes");
        nomDepartements.put("06", "Alpes-Maritimes");
        nomDepartements.put("07", "Ardèche");
        nomDepartements.put("08", "Ardennes");
        nomDepartements.put("09", "Ariège");
        nomDepartements.put("10", "Aube");
        nomDepartements.put("11", "Aude");
        nomDepartements.put("12", "Aveyron");
        nomDepartements.put("13", "Bouches-du-Rhône");
        nomDepartements.put("14", "Calvados");
        nomDepartements.put("15", "Cantal");
        nomDepartements.put("16", "Charente");
        nomDepartements.put("17", "Charente-Maritime");
        nomDepartements.put("18", "Cher");
        nomDepartements.put("19", "Corrèze");
        nomDepartements.put("2A", "Corse-du-Sud");
        nomDepartements.put("2B", "Haute-Corse");
        nomDepartements.put("21", "Côte-d'Or");
        nomDepartements.put("22", "Côtes-d'Armor");
        nomDepartements.put("23", "Creuse");
        nomDepartements.put("24", "Dordogne");
        nomDepartements.put("25", "Doubs");
        nomDepartements.put("26", "Drôme");
        nomDepartements.put("27", "Eure");
        nomDepartements.put("28", "Eure-et-Loir");
        nomDepartements.put("29", "Finistère");
        nomDepartements.put("30", "Gard");
        nomDepartements.put("31", "Haute-Garonne");
        nomDepartements.put("32", "Gers");
        nomDepartements.put("33", "Gironde");
        nomDepartements.put("34", "Hérault");
        nomDepartements.put("35", "Ille-et-Vilaine");
        nomDepartements.put("36", "Indre");
        nomDepartements.put("37", "Indre-et-Loire");
        nomDepartements.put("38", "Isère");
        nomDepartements.put("39", "Jura");
        nomDepartements.put("40", "Landes");
        nomDepartements.put("41", "Loir-et-Cher");
        nomDepartements.put("42", "Loire");
        nomDepartements.put("43", "Haute-Loire");
        nomDepartements.put("44", "Loire-Atlantique");
        nomDepartements.put("45", "Loiret");
        nomDepartements.put("46", "Lot");
        nomDepartements.put("47", "Lot-et-Garonne");
        nomDepartements.put("48", "Lozère");
        nomDepartements.put("49", "Maine-et-Loire");
        nomDepartements.put("50", "Manche");
        nomDepartements.put("51", "Marne");
        nomDepartements.put("52", "Haute-Marne");
        nomDepartements.put("53", "Mayenne");
        nomDepartements.put("54", "Meurthe-et-Moselle");
        nomDepartements.put("55", "Meuse");
        nomDepartements.put("56", "Morbihan");
        nomDepartements.put("57", "Moselle");
        nomDepartements.put("58", "Nièvre");
        nomDepartements.put("59", "Nord");
        nomDepartements.put("60", "Oise");
        nomDepartements.put("61", "Orne");
        nomDepartements.put("62", "Pas-de-Calais");
        nomDepartements.put("63", "Puy-de-Dôme");
        nomDepartements.put("64", "Pyrénées-Atlantiques");
        nomDepartements.put("65", "Hautes-Pyrénées");
        nomDepartements.put("66", "Pyrénées-Orientales");
        nomDepartements.put("67", "Bas-Rhin");
        nomDepartements.put("68", "Haut-Rhin");
        nomDepartements.put("69", "Rhône");
        nomDepartements.put("70", "Haute-Saône");
        nomDepartements.put("71", "Saône-et-Loire");
        nomDepartements.put("72", "Sarthe");
        nomDepartements.put("73", "Savoie");
        nomDepartements.put("74", "Haute-Savoie");
        nomDepartements.put("75", "Paris");
        nomDepartements.put("76", "Seine-Maritime");
        nomDepartements.put("77", "Seine-et-Marne");
        nomDepartements.put("78", "Yvelines");
        nomDepartements.put("79", "Deux-Sèvres");
        nomDepartements.put("80", "Somme");
        nomDepartements.put("81", "Tarn");
        nomDepartements.put("82", "Tarn-et-Garonne");
        nomDepartements.put("83", "Var");
        nomDepartements.put("84", "Vaucluse");
        nomDepartements.put("85", "Vendée");
        nomDepartements.put("86", "Vienne");
        nomDepartements.put("87", "Haute-Vienne");
        nomDepartements.put("88", "Vosges");
        nomDepartements.put("89", "Yonne");
        nomDepartements.put("90", "Territoire de Belfort");
        nomDepartements.put("91", "Essonne");
        nomDepartements.put("92", "Hauts-de-Seine");
        nomDepartements.put("93", "Seine-Saint-Denis");
        nomDepartements.put("94", "Val-de-Marne");
        nomDepartements.put("95", "Val-d'Oise");
        nomDepartements.put("971", "Guadeloupe");
        nomDepartements.put("972", "Martinique");
        nomDepartements.put("973", "Guyane");
        nomDepartements.put("974", "Réunion");
        nomDepartements.put("976", "Mayotte");
    }
    
    public static String getNomDepartement(String dpt){
        return nomDepartements.get(dpt);
    }
}
