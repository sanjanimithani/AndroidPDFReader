Sanjani Mithani

kotlinc-jvm 1.8.20
SDK: jbr-17 17.0.6
macOS Ventura 13.3

Sample Code Used: 
PanZoom, Orientation 

Icons: https://www.svgrepo.com/

Order of buttons on toolbar:
Draw - Pen, Highlight, Erase, Page Back, Page Right, Undo, Redo 

Modes:
The pen, highlighter or eraser stay selected until clicked again or another tool is selected which will unselect the
previously selected tool. To pan, no tool can be selected / active. The mode selected is displayed in the bottom status bar.

Panning: 
Implemented using single touch. As long as the mode value is "Mode:Pan" on bottom status bar or in other words the draw, 
highlight and erase button are not selected, user can drag on screen with mouse to pan. 

Zooming:
Implemented using multitouch/2 pinch. On Mac, press Ctrl to see the two pointers and continue to hold to pinch in/out. 

Pan and Zoom on Orientation Changes: 
The zoom and pan mode is reset when the orientation is altered. 

Pan and Zoom on Page Flip: 
Upon switching pages, the pan and zoom is also reset to ensure the entire page is visible to the user. 

Redo Stack: 
Redo stack is cleared when a new undoable action is taken aka draw, highlight or erase. This is based on the lecture 
slides. 
