#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Python script
:copyright:
    Ludwig Auer (ludwig.auer@tomo.ig.erdw.ethz.ch), 2014
:license:
    GNU General Public License, Version 3
    (http://www.gnu.org/copyleft/gpl.html)
"""

import sys, re

def main(argv):
    with open(argv[0]) as f:
        lines = f.readlines()
        
    ofile = open(argv[0]+".xml", "w")
    for line in lines:
        split_line=line.replace('\r','').split("%%%")
        ofile.write('<TWEET TOPICS="YES" LEWISSPLIT="TRAIN" CGISPLIT="TRAINING-SET" OLDID="'+str((split_line[0]))+'" NEWID="'+str((split_line[0]))+'">\n')
        ofile.write('<DATE>11-NOV-2014 11:11:11.11</DATE>\n')
        if split_line[1]=='negative':
            ofile.write('<TOPICS><D>negative</D></TOPICS>\n')
        elif split_line[1]=='positive':
            ofile.write('<TOPICS><D>positive</D></TOPICS>\n')
        ofile.write('<PLACES></PLACES>\n<PEOPLE></PEOPLE>\n<ORGS></ORGS>\n<EXCHANGES></EXCHANGES>\n<COMPANIES></COMPANIES>\n<UNKNOWN></UNKNOWN>\n<TEXT>\n<TITLE></TITLE>\n<DATELINE></DATELINE>\n<BODY>')
        ofile.write(' '.join(split_line[2].replace('"','').split())+'</BODY>\n')
        ofile.write('</TEXT>\n</TWEET>\n\n')
        print split_line[0]

if __name__ == "__main__":
    main(sys.argv[1:])                                                          
