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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

@SuppressWarnings("serial")
public class Arete extends DefaultWeightedEdge{

    long id;
    int modePose = -1;
    int nbLignes = 0;
    long idProprio;
    double longueur;
    List<NoeudInterne> intermediaires;
    private int idN1, idN2;
    List<double[]> points;
	public boolean dejaUtilise;

    public Arete(){} 

    public Arete(long id, int modePose){
        this.id = id;
        this.modePose = modePose;
        dejaUtilise = ! List.of(4, 11, 12, 13, 14).contains(modePose);
    }
    
    public Arete(long id, int modePose, long idProprio, double longueur){
    	this(id, modePose);
        this.idProprio = idProprio;
        this.longueur = longueur;
        this.intermediaires = new ArrayList<>();
        this.points = new ArrayList<>();
    }

    /*
    *   Correspond à la construction via la lecture du fichier Aretes_[NRO].csv
    *   ID / Noeud1 / Noeud2 / ModePose / Longueur / Id propriétaire /
    *   nb points (noeuds exclus) / [points : x2 / y2 / x3 / y3 etc.]
    */
    public Arete(String[] fields){
    	this(Long.parseLong(fields[0]), Integer.parseInt(fields[3]));
        idN1 = Integer.parseInt(fields[1]);
        idN2 = Integer.parseInt(fields[2]);
        longueur = Double.parseDouble(fields[4]);
        idProprio = Long.parseLong(fields[5]);
        points = new ArrayList<>();
        for (int i = 7;i<fields.length;i+=2){
            double[] coord = {Double.parseDouble(fields[i]),Double.parseDouble(fields[i+1])};
            points.add(coord);
        }
        dejaUtilise = ! List.of(4, 11, 12, 13, 14).contains(modePose);
    }

    public Arete(Arete ar){
    	this(ar.id, ar.modePose);
        this.nbLignes = ar.nbLignes;
        this.idProprio = ar.idProprio;
        this.points = ar.points;
    }

    public void addIntermediaire(NoeudInterne n){
        intermediaires.add(n);
        this.points.add(n.coord);
    }

    public void addIntermediaires(List<double[]> intermediaires, int indice, NoeudAGarder n1, NoeudAGarder n2, Reseau reseau){
        Node precedent = n1;
        double distance = 0;
        for(double coord[] : intermediaires) {
            distance+=precedent.distance(coord);
            precedent = reseau.getNoeudInterne(coord, indice, n1, n2, this, distance);
            this.intermediaires.add((NoeudInterne) precedent);
            this.points.add(coord);
        }
    }

    public void changeAndAddIntermediaires(List<NoeudInterne> noeuds, NoeudAGarder n1, NoeudAGarder n2){
        Node precedent = n1;
        double distance = 0;
        for(NoeudInterne n : noeuds) {
            distance+=precedent.distance(n);
            n.actualise(this, n1, n2, distance);
            intermediaires.add(n);
            points.add(n.coord);
            precedent = n;
        }
    }
    
    public void print(PrintWriter writer, NoeudAGarder n1, NoeudAGarder n2){
        writer.print(id+";"+n1.id+";"+n2.id+";");
        writer.print(modePose+";"+longueur+";"+idProprio+";"+intermediaires.size());
        for (NoeudInterne n : intermediaires){
            writer.print(";"+n.coord[0]+";"+n.coord[1]);
        }
        writer.println();
    }

    public int getAutreExtremite(int id){
        if (id == idN1) return idN2;
        else if (id == idN2) return idN1;
        else {
            System.out.println("Problème dans le path NRO-PC");
            return -1;
        }
    }
    
    /*
    *   Fonction utilisée pour constituer les shapefiles de sortie Aretes_[dept].csv
    *   Constitue un vecteur de coordonnées avec les noeuds extrémités
    *   et les points internes
    *   Amélioration 2019 : la fonction renvoie désormais les points
    *   dans le "bon ordre" quel que soit le sens des noeuds
    */
    public Coordinate[] getPoints(NoeudAGarder n1, NoeudAGarder n2){
        int n = 2;
        if (points != null) n = points.size()+2;
        
        Coordinate[] coords = new Coordinate[n];
        coords[0] = new Coordinate(n1.coord[0], n1.coord[1]);
        if(n > 2) {
            if(n == 3) {
                double[] coord = points.get(0);
                coords[1] = new Coordinate(coord[0], coord[1]);
            } else {
                // Pour connaitre le sens dans lequel ajouter les points, on compare
                // la somme des distances des extrémités aux premier et dernier points
                // dans un sens et dans l'autre
                if(n1.distance(points.get(0)) +  n2.distance(points.get(n-3))  < n1.distance(points.get(n-3)) + n2.distance(points.get(0))) {
                    // n1 est bien le "premier" point de l'arête
                    for (int i = 0;i<n-2;i++){
                        double[] coord = points.get(i);
                        coords[i + 1] = new Coordinate(coord[0], coord[1]);
                    }
                } else {
                    // n1 est le "dernier" point de l'arête
                    for (int i = 0;i<n-2;i++){
                        double[] coord = points.get(i);
                        coords[n - i - 2] = new Coordinate(coord[0], coord[1]);
                    }
                }
            }
        }
        
        coords[n-1] = new Coordinate(n2.coord[0], n2.coord[1]);
        return coords;
    }
    
    /**
     * Fonction permettant d'initialiser l'objet FeatureType pour le tracé
     * des shapefiles des arêtes
     * 
     * @param crs (CoordinateReferenceSystem) Système de coordonnées de référence pour l'écriture du shapefile
     * @return SimpleFeatureType spécifiant les attributs pour le tracé de l'arête
     */
    public static SimpleFeatureType getFeatureType(CoordinateReferenceSystem crs){
        SimpleFeatureTypeBuilder builderLineaires = new SimpleFeatureTypeBuilder();
        builderLineaires.setName("ARETE");
        builderLineaires.setCRS(crs);
        builderLineaires.add("the_geom", MultiLineString.class);
        builderLineaires.add("ID", Integer.class);
        builderLineaires.length(13).add("ID_RSO", String.class);
        builderLineaires.add("LONGUEUR", Double.class);
        builderLineaires.add("LONG_POND", Double.class);
        builderLineaires.add("MODE_POSE", Integer.class);
        return builderLineaires.buildFeatureType();
    }
    
    /**
     * Fonction permettant de créer la Feature correspondant au tracé de l'arête
     * dans un shapefile
     * 
     * @param pts (Coordinate[]) Coordonnées des points de l'arête
     * @param idReseau (String) Nom du NRO / zone
     * @param longeurPonderee (double) longueur de l'arête dans le graphe
     * @param gf
     * @param featureBuilderLineaires SimpleFeatureBuilder permettant le tracé de la SimpleFeature
     * @return SimpleFeature représentant le tracé de l'arête
     */
    public SimpleFeature getFeature(Coordinate[] pts, String idReseau, double longeurPonderee, GeometryFactory gf,SimpleFeatureBuilder featureBuilderLineaires){
        
        featureBuilderLineaires.add(gf.createMultiLineString(new LineString[]{gf.createLineString(pts)}));
        featureBuilderLineaires.add(this.id);
        featureBuilderLineaires.add(idReseau);
        featureBuilderLineaires.add(this.longueur);
        featureBuilderLineaires.add(longeurPonderee);
        featureBuilderLineaires.add(this.modePose);
        
        return featureBuilderLineaires.buildFeature(null);
    }

	public int getModePose() {
		return modePose;
	}

	public double getLongueur() {
		return longueur;
	}
	
	public long getId() {
		return id;
	}
}