package nextstep.subway.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.OneToMany;

import nextstep.subway.domain.exception.ExceptionMessage;

@Embeddable
public class Sections {
	private final int NOT_FOUND_INDEX = -1;
	private final int MIN_SIZE = 1;

	@OneToMany(mappedBy = "line", cascade = {CascadeType.ALL, CascadeType.MERGE}, orphanRemoval = true)
	private List<Section> sections = new ArrayList<>();

	public void addSection(Line line, Station upStation, Station downStation, int distance) {
		if(sections.isEmpty()) {
			sections.add(new Section(line, upStation, downStation, distance));
			return;
		}

		validDuplicationSection(upStation, downStation);
		validIncludedAnyStation(upStation, downStation);

		updateMiddleSection(line, upStation, downStation, distance);

		sections.add(new Section(line, upStation, downStation, distance));
	}

	public List<Section> getSections() {
		return Collections.unmodifiableList(sections);
	}

	public List<Station> getStations() {
		if (sections.isEmpty()) {
			return Collections.emptyList();
		}

		List<Station> downStations = sections.stream()
			.map(Section::getDownStation)
			.collect(Collectors.toList());

		Section firstSection = sections.stream()
			.filter(it -> !downStations.contains(it.getUpStation()))
			.findFirst()
			.orElseThrow(IllegalArgumentException::new);

		List<Station> stations = new ArrayList<>();
		stations.add(firstSection.getUpStation());
		stations.add(firstSection.getDownStation());

		Optional<Section> sectionOptional = findSectionByUpStation(firstSection.getDownStation());

		while(sectionOptional.isPresent()) {
			Station station = sectionOptional.get().getDownStation();

			stations.add(station);
			sectionOptional = findSectionByUpStation(station);
		}

		return stations;
	}

	public void deleteLastSection(Station station) {
		validEmpty();
		validLastDownStation(station);
		sections.removeIf(it -> it.isDownStation(station));
	}

	public void deleteMiddleSection(Station station) {
		validEmpty();

		Optional<Section> leftSection = findSectionByDownStation(station);
		Optional<Section> rightSection = findSectionByUpStation(station);

		if(leftSection.isPresent() && rightSection.isPresent()) {
			Section left = leftSection.orElseThrow(IllegalArgumentException::new);
			Section right = rightSection.orElseThrow(IllegalArgumentException::new);

			sections.remove(left);
			sections.remove(right);

			sections.add(new Section(left.getLine(), left.getUpStation(), right.getDownStation(),
				left.getDistance() + right.getDistance()));
			return;
		}

		if(leftSection.isPresent() && !rightSection.isPresent()) {
			Section left = leftSection.orElseThrow(IllegalArgumentException::new);
			sections.remove(left);
			return;
		}

		if(!leftSection.isPresent() && rightSection.isPresent()) {
			Section right = rightSection.orElseThrow(IllegalArgumentException::new);
			sections.remove(right);
			return;
		}

		throw new IllegalArgumentException();
	}

	private void validDuplicationSection(Station upStation, Station downStation) {
		sections.stream()
			.filter(it -> it.isDuplicateStation(upStation, downStation))
			.findFirst()
			.ifPresent(it -> { throw new IllegalArgumentException(ExceptionMessage.DUPLICATE_SECTION.getMessage()); });
	}

	private void validIncludedAnyStation(Station upStation, Station downStation) {
		sections.stream()
			.filter(it -> it.isContainStation(upStation) || it.isContainStation(downStation))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(ExceptionMessage.DO_NOT_ADD_SECTION.getMessage()));
	}

	private void updateMiddleSection(Line line, Station upStation, Station downStation, int distance) {
		int index = IntStream.range(0, sections.size())
			.filter(i -> sections.get(i).isUpStation(upStation)  || sections.get(i).isDownStation(downStation))
			.findFirst()
			.orElse(NOT_FOUND_INDEX);

		if(index == NOT_FOUND_INDEX) {
			return;
		}

		Section section = sections.get(index);
		if(section.isGraterOrEqualThanExistingDistance(distance)) {
			throw new IllegalArgumentException(ExceptionMessage.DO_NOT_ADD_SECTION.getMessage());
		}

		if(section.isUpStation(upStation)) {
			Section updateSection = new Section(line, downStation, section.getDownStation(), section.getDistance() - distance);
			sections.remove(index);
			sections.add(index, updateSection);
		}

		if(section.isDownStation(downStation)) {
			Section updateSection = new Section(line, section.getUpStation(), upStation, section.getDistance() - distance);
			sections.remove(index);
			sections.add(index, updateSection);
		}
	}

	private void validEmpty() {
		if(sections.isEmpty() || sections.size() == MIN_SIZE) {
			throw new IllegalArgumentException(ExceptionMessage.NOT_REMOVE_SECTION.getMessage());
		}
	}

	private void validLastDownStation(Station station) {
		Section lastSection = sections.get(sections.size() - 1);

		if(!lastSection.isDownStation(station)) {
			throw new IllegalArgumentException(ExceptionMessage.NOT_REMOVE_SECTION.getMessage());
		}
	}

	private Optional<Section> findSectionByUpStation(Station station) {
		return sections.stream()
			.filter(it -> it.isUpStation(station))
			.findFirst();
	}

	private Optional<Section> findSectionByDownStation(Station station) {
		return sections.stream()
			.filter(it -> it.isDownStation(station))
			.findFirst();
	}
}
