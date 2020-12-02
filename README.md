# Diplomová práca - Podpora nástroja Protégé pre prácu v CWA móde

#### Prerekvizity
Predtým než si vytvoríme CWA plugin je potrebné si stiahnuť následovné programy:
+ Apache Maven vo verzii 3.1.0 alebo vyššiu (https://maven.apache.org/index.html).
+ Protégé desktop verziu 5.5.0 alebo novšiu. (https://protege.stanford.edu/products.php#desktop-protege).
+ Eclipse IDE for Enterprise Java Developers. Prípadne iné vývojové prostredie. (https://www.eclipse.org/).
+ Java version 1.8 alebo vyššia.
+ Git (https://git-scm.com/) V prípade, ak si chcete stiahnuť aktuálnu verziu plug-inu. 

#### Postup pre build:
1. Stiahnutie vzorového projektu z repozitára:
	git clone https://github.com/lemros/diplomovka.git
2. Vstúpiť do adresára ''diplomovka'':
	cd diplomovka
3. Použiť príkaz ''mvn clean package'' na zostavenie projektu. 
4. V priečinku ''target'' následne nájdeme zostavený plug-in - thesis.cwa-1.0.0.jar.
5. Skopírovať súbor JAR do priečinku ''plugins'' nachádzajúci sa v domovskom adresári nástroja Protégé.