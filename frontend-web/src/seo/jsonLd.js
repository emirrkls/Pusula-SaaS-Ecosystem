export function injectJsonLd(id, data) {
    removeJsonLd(id);
    if (!data) return;

    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.id = id;
    script.textContent = JSON.stringify(data);
    document.head.appendChild(script);
}

export function removeJsonLd(id) {
    document.head.querySelector(`#${id}`)?.remove();
}
