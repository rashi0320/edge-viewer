const sampleBase64 = localStorage.getItem('sample_frame_base64') || '';
const img = document.getElementById('frame') as HTMLImageElement;
const stats = document.getElementById('stats') as HTMLElement;
if (sampleBase64 && img) {
  img.src = sampleBase64;
  img.onload = () => {
    stats.innerText = `Resolution: ${img.naturalWidth}x${img.naturalHeight} | FPS: sample`;
  };
} else {
  stats.innerText = 'No sample frame found. Save base64 to localStorage key "sample_frame_base64".';
}
