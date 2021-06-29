class MapControlIcons
{
	constructor(oHtml, sId)
	{
		this.oHtml = oHtml;
		this.sId = sId;
	}
	
	onAdd(map)
	{
		this._map = map;
		this._container = document.createElement('div');
		this._container.setAttribute('id', this.sId);
		this._container.className = 'mapboxgl-ctrl mapboxgl-ctrl-group six-button-control';

		
		this._container.innerHTML = this.oHtml
			.reduce((accum, opts) => accum +
				`<button class="mapboxgl-ctrl-icon" type="button" title="${opts.t}"${opts.id !== undefined ? ' id="' + opts.id + '"' :''}>
					<img src="images/button-control/${opts.i}.png" />
				</button>`, '');

		return this._container;
	}

	onRemove()
	{
		this._container.parentNode.removeChild(this._container);
		this._map = undefined;
	}
}

export {MapControlIcons};