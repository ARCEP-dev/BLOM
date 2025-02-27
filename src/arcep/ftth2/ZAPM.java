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

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;


public class ZAPM {
    
    private final int id;
    private final String zone;
    private final UO uo;
    private final double x;
    private final double y;
    private final double distanceNRO;
    
    // connaissance des longueurs PM-PBO
    private double longueurMin;
    private double longueurMax;
    private double longueurTotale;
    private final int[] distributionLongueurs;    

    public ZAPM(Noeud n, double distance, String zone, Parametres parametres){
        this.id = n.numPM;
        this.zone = zone;
        uo = new UO(zone, true, false, parametres);
        uo.setLignesEtPM(n.taillePM(zone), parametres);

        this.x = n.coord[0];
        this.y = n.coord[1];
        this.distanceNRO = distance;
        longueurMin = Double.POSITIVE_INFINITY;
        longueurMax = 0;
        longueurTotale = 0;
        distributionLongueurs = new int[2000];
        distributionLongueurs[0] = n.demandeLocaleExt(zone);
    }

    public void addArete(AreteBLOM a, Parametres parametres, double distancePM){
        uo.addLineaires(a.getLineaires(parametres, true, zone));
        uo.addPBO((int) Math.ceil(a.n.demandeLocaleExt(zone)/(double) parametres.nbMaxLignesPBO), a.modePoseSortie);
        if(!a.n.isPMExt(zone)){
            int demandeLocale = a.n.demandeLocaleExt(zone);
            if (demandeLocale > 0){
                if (distancePM < longueurMin) longueurMin = distancePM;
                if (distancePM > longueurMax) longueurMax = distancePM;
            }
            distributionLongueurs[Math.min(1999, (int) Math.floor(distancePM/50))] += demandeLocale;
            this.longueurTotale += demandeLocale*distancePM;
        }
    }

    public int getID(){
        return this.id;
    }

    public void addIncomingCables(AreteBLOM a, Parametres parametres){
        int[] cablesFille = a.getCables(true, zone);
        uo.addIncomingCables(cablesFille, true, parametres.calibre, parametres.getCalibreMin());
    }

    public UO getUO(){
        return uo;
    }

    public String getStringUO(String codePM, Parametres parametres, int detail){
        return codePM+";"+x+";"+y+";"+String.valueOf(distanceNRO).replace(".", ",")+";"+uo.print(parametres, detail);
    }

    public String getStringDistri(){
        String ligne = String.valueOf(distanceNRO).replace(".", ",")+";"
                +String.valueOf(longueurMin).replace(".", ",")+";"
                +String.valueOf(longueurMax).replace(".", ",");
        for (int i : distributionLongueurs){
            ligne+=";"+i;
        }
        return ligne;
    }

    public double percentile(double pourcent){
        int compteur = 0;
        int seuil = (int) Math.floor(this.uo.getNbLignes()*pourcent);
        for (int i = 0;i<this.distributionLongueurs.length;i++){
            compteur += this.distributionLongueurs[i];
            if (compteur >=  seuil)
                return 25+i*50;
        }
        return Double.NaN;
    }
    
    public static SimpleFeatureType getPMFeatureType(CoordinateReferenceSystem crs){
        SimpleFeatureTypeBuilder builderPM = new SimpleFeatureTypeBuilder();
        builderPM.setName("PointsMutualisation");
        builderPM.setCRS(crs);
        builderPM.add("the_geom", Point.class);
        builderPM.length(4).add("ZONE_MACRO", String.class);
        builderPM.length(13).add("NRO", String.class);
        builderPM.length(6).add("TYPE", String.class);
        builderPM.add("ID", Integer.class);
        builderPM.add("NB_LIGNES", Integer.class);
        builderPM.add("NB_FIBRES", Integer.class);
        builderPM.add("NBARMOIRES", Integer.class);
        return builderPM.buildFeatureType();
    }
    
    public SimpleFeature getFeaturePM(String CodeNRO, String zoneMacro, GeometryFactory gf, SimpleFeatureBuilder featureBuilderPM){
        featureBuilderPM.add(gf.createPoint(new Coordinate(x, y)));
        featureBuilderPM.add(zoneMacro);
        featureBuilderPM.add(CodeNRO);
        if (zone.equals("ZTD_HD"))
            featureBuilderPM.add("PM100");
        else
            featureBuilderPM.add("PM300");
        featureBuilderPM.add(this.id);
        featureBuilderPM.add(uo.getNbLignes());
        featureBuilderPM.add(uo.getNbIncomingFibres(true));
        featureBuilderPM.add(uo.getNbArmoires());
        return featureBuilderPM.buildFeature(null);
    }

    public String getNROPM(Parametres parametres){
        String type = "PM300";
        if (zone.equals("ZTD_HD"))
            type = "PM100";
        return type+";"+String.valueOf(distanceNRO).replace(".", ",")+";"+uo.getNbLignes() + uo.printIncomingCables(true)+";"+String.valueOf(this.longueurTotale/this.uo.getNbLignes()).replace(".", ",");
    }
    
}
