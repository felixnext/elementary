#!/usr/bin/perl

# Program to filter Wikipedia XML dumps to "clean" text consisting only of lowercase
# letters (a-z, converted from A-Z), and spaces (never consecutive).
# All other characters are converted to spaces.  Only text which normally appears
# in the web browser is displayed.  Tables are removed.  Image captions are
# preserved.  Links are converted to normal text.  Digits are spelled out.

# execute with: perl wikifil.pl enwik9 > text


# Adapted for the german language based on the script written by Matt Mahoney
# http://mattmahoney.net/dc/textdata.html
# This program is released to the public domain.

$/=">";                     # input record separator
$a = 0;
while (<>) {
 if (/<text /) {$text=1;}  # remove all but between <text> ... </text>
 if (/#redirect/i) {$text=0;}  # remove #REDIRECT
 if ($text) {


   # Remove any text not normally visible
   if (/<\/text>/) {$text=0;}
   s/<.*>//;               # remove xml tags
   s/&amp;/&/g;            # decode URL encoded chars
   s/&lt;/</g;
   s/&gt;/>/g;
   s/<ref[^<]*<\/ref>//g;  # remove references <ref...> ... </ref>
   s/<[^>]*>//g;           # remove xhtml tags
   s/\[http:[^] ]*/[/g;    # remove normal url, preserve visible text
   s/\|thumb//ig;          # remove images links, preserve caption
   s/\|left//ig;
   s/\|right//ig;
   s/\|\d+px//ig;
   s/\[\[image:[^\[\]]*\|//ig;
   s/\[\[category:([^|\]]*)[^]]*\]\]/[[$1]]/ig;  # show categories without markup
   s/\[\[[a-z\-]*:[^\]]*\]\]//g;  # remove links to other languages
   s/\[\[[^\|\]]*\|/[[/g;  # remove wiki url, preserve visible text
   s/{{[^}]*}}//g;         # remove {{icons}} and {tables}
   s/{[^}]*}//g;
   s/\[//g;                # remove [ and ]
   s/\]//g;
   s/&[^;]*;/ /g;          # remove URL encoded chars


   # convert to lowercase letters and spaces, spell digits
   $_=" $_ ";
   tr/A-Z/a-z/;
   tr/ÄÖÜ/äöü/; # convert upper letter umlaut to normal characters
   s/ä/ae/g; # change characters to test
   s/ö/oe/g;
   s/ü/ue/g;
   s/0/ zero /g;
   s/1/ one /g;
   s/2/ two /g;
   s/3/ three /g;
   s/4/ four /g;
   s/5/ five /g;
   s/6/ six /g;
   s/7/ seven /g;
   s/8/ eight /g;
   s/9/ nine /g;
   tr/a-z/ /cs;
   chop;
   print $_;
   $num1 = $_ =~ tr/ //;
   $a = $a + $num1;
   if($a > 100) {
     print "\n";
     $a = 0;
   }
 }
}
